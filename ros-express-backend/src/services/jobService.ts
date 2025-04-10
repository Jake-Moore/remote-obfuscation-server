import fs from "fs";
import path from "path";
import colors from "colors";

interface Job {
    id: string;
    status: "pending" | "processing" | "completed" | "failed";
    error?: string;
    jarPath?: string;
    configPath?: string;
    outputPath?: string;
}

const jobs = new Map<string, Job>();

export function createJob(
    requestID: string,
    jarPath: string,
    configPath: string,
    outputPath: string
): Job {
    const job: Job = {
        id: requestID,
        status: "pending",
        jarPath,
        configPath,
        outputPath,
    };
    jobs.set(requestID, job);
    return job;
}

export function getJob(requestID: string): Job | undefined {
    return jobs.get(requestID);
}

export function updateJobStatus(
    requestID: string,
    status: Job["status"],
    error?: string
): void {
    const job = jobs.get(requestID);
    if (!job) {
        console.error(colors.red(`Job ${requestID} not found`));
        return;
    }
    job.status = status;
    if (error) {
        job.error = error;
    }
    console.log(colors.gray(`[Job] ${requestID} status updated to ${status}`));
    jobs.set(requestID, job);
}

export function cleanupJob(requestID: string): void {
    const job = jobs.get(requestID);
    if (!job) {
        return;
    }

    // Delete temporary files
    if (job.jarPath) {
        fs.unlink(job.jarPath, (err) => {
            if (err && err.code !== "ENOENT") {
                console.error(
                    colors.yellow(`Error deleting jar file: ${job.jarPath}`)
                );
            }
        });
    }
    if (job.configPath) {
        fs.unlink(job.configPath, (err) => {
            if (err && err.code !== "ENOENT") {
                console.error(
                    colors.yellow(
                        `Error deleting config file: ${job.configPath}`
                    )
                );
            }
        });
    }

    jobs.delete(requestID);
}

// Cleanup jobs older than 1 hour
setInterval(() => {
    const oneHourAgo = Date.now() - 60 * 60 * 1000;
    for (const [requestID, job] of jobs.entries()) {
        const jobTimestamp = parseInt(requestID.split("-")[0]);
        if (jobTimestamp < oneHourAgo) {
            cleanupJob(requestID);
        }
    }
}, 15 * 60 * 1000); // Run every 15 minutes
