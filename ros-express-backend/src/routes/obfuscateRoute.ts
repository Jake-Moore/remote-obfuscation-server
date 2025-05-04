import express, { Request, Response, NextFunction } from "express";
import multer from "multer";
import colors from "colors";
import fs from "fs";
// Service Methods
import { Obfuscator } from "../services/obfuscators/Obfuscator.js";
import { getObfuscator } from "../services/obfuscators/ObfuscatorLoader.js";
import {
    createJob,
    getJob,
    updateJobStatus,
    cleanupJob,
} from "../services/jobService.js";
import { generateUUIDFragment } from "../services/obfuscators/allatori/AllatoriConfigGenerator.js";
import { addToQueue, getQueueLength, getJobQueueIndex } from "../services/queueService.js";

const router = express.Router();

// Configure multer to handle file uploads
const upload = multer({
    dest: process.env.ROS_UPLOADS_OBF_STORAGE || "uploads-obf/",
});

// Define a type for the expected files structure
interface RequestFiles {
    jar?: Express.Multer.File[];
    config?: Express.Multer.File[];
}

// Express Endpoint (POST)
router.post(
    "/",
    upload.fields([{ name: "jar" }, { name: "config" }]),
    async (req: Request, res: Response, next: NextFunction): Promise<void> => {
        const files = req.files as RequestFiles;
        // Check if both files are provided
        if (!files || !files.jar || !files.config) {
            const err = new Error(
                `You must provide both a 'jar' and 'config' file fields (types: jar & xml).`
            );
            (err as any).status = 400; // Bad Request
            return next(err);
        }

        // Parse files from request
        const jarFile = files.jar[0];
        const configFile = files.config[0];
        
        // Extract requested_by parameter from query params if it exists
        const requestedBy = req.query.requested_by as string | undefined;

        // Obtain an obfuscator instance
        const obfuscator: Obfuscator = getObfuscator();

        try {
            // Safety Check: ensure the jar is not already watermarked/obfuscated
            if (
                await obfuscator.isJarWatermarked(req, res, next, jarFile.path)
            ) {
                const err = new Error(
                    "The provided jar file is already obfuscated."
                );
                (err as any).status = 400;
                return next(err);
            }

            // Create a Unique Request ID that identifies this obfuscation request and its log
            const requestID = `${Date.now()}-${generateUUIDFragment()}`;
            const outputPath = `${
                process.env.ROS_UPLOADS_OBF_STORAGE || "uploads-obf/"
            }${requestID}.jar`;

            // Create job and store file paths
            const job = createJob(
                requestID,
                jarFile.path,
                configFile.path,
                outputPath
            );

            // Add job to queue and get its position and size
            const queueResult = addToQueue("obfuscate", {
                id: requestID,
                type: "obfuscate",
                process: async () => {
                    try {
                        updateJobStatus(requestID, "processing");
                        await obfuscator.obfuscate(
                            req,
                            res,
                            next,
                            jarFile,
                            configFile,
                            requestID,
                            requestedBy
                        );
                        updateJobStatus(requestID, "completed");
                    } catch (error) {
                        console.error(
                            colors.red(`Error in background obfuscation: ${error}`)
                        );
                        updateJobStatus(
                            requestID,
                            "failed",
                            error instanceof Error ? error.message : String(error)
                        );
                    }
                },
            });

            console.log(colors.gray(`[Obfuscate] Request ${requestID} queued at position ${queueResult.index + 1}/${queueResult.size}`));

            // Return immediately with job ID and queue information
            res.status(202).json({
                message: "Obfuscation job queued",
                request_id: requestID,
                status: job.status,
                queue_index: queueResult.index,
                total_queue_size: queueResult.size,
            });
        } catch (error) {
            console.error(colors.red(`Error starting obfuscation: ${error}`));
            const err = new Error(
                "Error starting obfuscation. Please check the server logs."
            );
            (err as any).status = 500;
            next(err);
        }
    }
);

// Status check endpoint
router.get(
    "/:requestId",
    async (req: Request, res: Response, next: NextFunction): Promise<void> => {
        const requestId = req.params.requestId;
        const job = getJob(requestId);

        if (!job) {
            const err = new Error("Job not found");
            (err as any).status = 404;
            return next(err);
        }

        // Get queue position if job is still in queue
        let jobQueueIndex = -1;
        if (job.status === "pending" || job.status === "processing") {
            jobQueueIndex = getJobQueueIndex("obfuscate", requestId);
        }

        if (job.status === "failed") {
            const err = new Error(job.error || "Obfuscation failed");
            (err as any).status = 500;
            cleanupJob(requestId);
            return next(err);
        }

        // Return job status and queue information
        res.status(200).json({
            message: job.status === "completed" ? "Job completed successfully" : "Job is still processing",
            request_id: requestId,
            status: job.status,
            queue_index: jobQueueIndex,
            total_queue_size: getQueueLength("obfuscate")
        });
    }
);

// New endpoint for downloading the obfuscated jar
router.get(
    "/:requestId/download",
    async (req: Request, res: Response, next: NextFunction): Promise<void> => {
        const requestId = req.params.requestId;
        const job = getJob(requestId);

        if (!job) {
            const err = new Error("Job not found");
            (err as any).status = 404;
            return next(err);
        }

        if (job.status !== "completed" || !job.outputPath || !fs.existsSync(job.outputPath)) {
            const err = new Error("Obfuscated jar not available");
            (err as any).status = 404;
            return next(err);
        }

        try {
            // Set appropriate headers for file download
            res.setHeader('Content-Type', 'application/java-archive');
            res.setHeader('Content-Disposition', `attachment; filename="${requestId}.jar"`);
            
            // Stream the file
            const fileStream = fs.createReadStream(job.outputPath);
            fileStream.pipe(res);

            // Cleanup after streaming is complete
            fileStream.on('end', () => {
                cleanupJob(requestId);
            });

            fileStream.on('error', (error) => {
                console.error(colors.red(`Error streaming file: ${error}`));
                cleanupJob(requestId);
                next(error);
            });
        } catch (error) {
            console.error(colors.red(`Error handling download: ${error}`));
            cleanupJob(requestId);
            next(error);
        }
    }
);

router.get("/", (_req: Request, res: Response) => {
    res.status(200).json({ message: "Obfuscation API is ready." });
});

export default router;
