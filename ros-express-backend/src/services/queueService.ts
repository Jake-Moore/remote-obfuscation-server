import colors from "colors";

type QueueType = "obfuscate" | "watermark" | "stacktrace";

interface QueueItem {
    id: string;
    type: QueueType;
    process: () => Promise<void>;
}

interface QueueAddResult {
    position: number;  // 0-based position where the item was added
    size: number;      // Total queue size at the time of addition
}

class Queue {
    private items: QueueItem[] = [];
    private isProcessing = false;

    /**
     * Adds an item to the queue and returns its position and the queue size at the time of addition.
     * The queue processing is automatically started if it's not already running.
     * 
     * Queue execution flow:
     * 1. Item is added to the queue
     * 2. If no processing is happening, processNext() is called
     * 3. processNext() runs the item's process() function
     * 4. After completion, processNext() is called again if there are more items
     * 5. This continues until the queue is empty
     * 
     * The queue maintains its own state and doesn't need external supervision.
     * Processing is triggered by:
     * - The first add() call when the queue is empty
     * - Each processNext() call when there are remaining items
     */
    async add(item: QueueItem): Promise<QueueAddResult> {
        const position = this.items.length;
        const size = position + 1; // Size after adding this item
        this.items.push(item);
        console.log(colors.gray(`[Queue:${item.type}] Job ${item.id} added at position ${position + 1}/${size}`));
        
        if (!this.isProcessing) {
            await this.processNext();
        }
        return { position, size };
    }

    private async processNext(): Promise<void> {
        if (this.items.length === 0) {
            this.isProcessing = false;
            return;
        }

        this.isProcessing = true;
        const item = this.items[0];

        try {
            console.log(colors.gray(`[Queue:${item.type}] Starting job ${item.id} (${this.items.length} remaining)`));
            await item.process();
            console.log(colors.gray(`[Queue:${item.type}] Completed job ${item.id}`));
        } catch (error) {
            console.error(colors.red(`[Queue:${item.type}] Error processing job ${item.id}: ${error}`));
        } finally {
            this.items.shift(); // Remove the processed item
            if (this.items.length > 0) {
                console.log(colors.gray(`[Queue:${item.type}] ${this.items.length} jobs remaining`));
            } else {
                console.log(colors.gray(`[Queue:${item.type}] Queue is empty`));
            }
            await this.processNext(); // Process next item
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

export async function addToQueue(type: QueueType, item: QueueItem): Promise<QueueAddResult> {
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