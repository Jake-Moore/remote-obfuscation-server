import colors from "colors";

type QueueType = "obfuscate" | "watermark" | "stacktrace";

interface QueueItem {
    id: string;
    type: QueueType;
    process: () => Promise<void>;
    timeout?: number; // Optional timeout in milliseconds
}

interface QueueAddResult {
    position: number;  // 0-based position where the item was added
    size: number;      // Total queue size at the time of addition
}

class Queue {
    private static readonly MAX_QUEUE_SIZE = 1000;
    private static readonly DEFAULT_TIMEOUT = 180000; // 180 seconds default timeout

    private items: QueueItem[] = [];
    private isProcessing = new Int32Array(new SharedArrayBuffer(4)); // Atomic flag
    private processingPromise: Promise<void> | null = null;

    /**
     * Adds an item to the queue and returns immediately with its position and size.
     * Queue processing is handled asynchronously by the processQueue method.
     * @throws Error if queue is full
     */
    add(item: QueueItem): QueueAddResult {
        if (this.items.length >= Queue.MAX_QUEUE_SIZE) {
            throw new Error(`Queue is full (max size: ${Queue.MAX_QUEUE_SIZE})`);
        }

        const position = this.items.length;
        const size = position + 1; // Size after adding this item
        this.items.push(item);
        console.log(colors.gray(`[Queue:${item.type}] Job ${item.id} added at position ${position + 1}/${size}`));
        
        // Start processing if not already running
        this.startProcessing();
        
        return { position, size };
    }

    /**
     * Safely starts the queue processing if it's not already running.
     * Uses atomic operations to prevent race conditions.
     */
    private startProcessing(): void {
        // Try to set the processing flag from 0 to 1
        if (Atomics.compareExchange(this.isProcessing, 0, 0, 1) === 0) {
            // We successfully acquired the lock
            if (this.items.length > 0) {
                // Start processing if there are items
                this.processingPromise = this.processQueue();
                this.processingPromise.finally(() => {
                    // Reset the processing flag when done
                    Atomics.store(this.isProcessing, 0, 0);
                });
            } else {
                // No items to process, release the lock
                Atomics.store(this.isProcessing, 0, 0);
            }
        }
    }

    /**
     * Asynchronously processes the queue items one at a time.
     * This method runs independently of the add method.
     */
    private async processQueue(): Promise<void> {
        while (this.items.length > 0) {
            const item = this.items[0];
            const timeout = item.timeout ?? Queue.DEFAULT_TIMEOUT;
            
            try {
                console.log(colors.gray(`[Queue:${item.type}] Starting job ${item.id} (${this.items.length} remaining)`));
                
                // Create a promise that rejects after timeout
                const timeoutPromise = new Promise((_, reject) => {
                    setTimeout(() => {
                        reject(new Error(`Job ${item.id} timed out after ${timeout}ms`));
                    }, timeout);
                });

                // Race between the actual process and the timeout
                await Promise.race([
                    item.process(),
                    timeoutPromise
                ]);

                console.log(colors.gray(`[Queue:${item.type}] Completed job ${item.id}`));
            } catch (error) {
                if (error instanceof Error && error.message.includes('timed out')) {
                    console.error(colors.red(`[Queue:${item.type}] Timeout processing job ${item.id}`));
                } else {
                    console.error(colors.red(`[Queue:${item.type}] Error processing job ${item.id}: ${error}`));
                }
            } finally {
                this.items.shift(); // Remove the processed item
                if (this.items.length > 0) {
                    console.log(colors.gray(`[Queue:${item.type}] ${this.items.length} jobs remaining`));
                } else {
                    console.log(colors.gray(`[Queue:${item.type}] Queue is empty`));
                }
            }
        }
    }

    getQueueLength(): number {
        return this.items.length;
    }
}

// Create queues for each route type
const queues = new Map<QueueType, Queue>();

// Initialize queues
["obfuscate", "watermark", "stacktrace"].forEach((type) => {
    queues.set(type as QueueType, new Queue());
});

export function addToQueue(type: QueueType, item: QueueItem): QueueAddResult {
    const queue = queues.get(type);
    if (!queue) {
        throw new Error(`Invalid queue type: ${type}`);
    }
    return queue.add(item);
}

export function getQueueLength(type: QueueType): number {
    const queue = queues.get(type);
    if (!queue) {
        throw new Error(`Invalid queue type: ${type}`);
    }
    return queue.getQueueLength();
} 