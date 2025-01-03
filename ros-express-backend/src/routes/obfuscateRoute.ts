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

            // Start obfuscation process in background
            (async () => {
                try {
                    updateJobStatus(requestID, "processing");
                    await obfuscator.obfuscate(
                        req,
                        res,
                        next,
                        jarFile,
                        configFile,
                        requestID
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
            })();

            // Return immediately with job ID
            res.status(202).json({
                message: "Obfuscation job started",
                request_id: requestID,
                status: job.status,
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

        if (
            job.status === "completed" &&
            job.outputPath &&
            fs.existsSync(job.outputPath)
        ) {
            // Read the jar file and send it in the response
            const base64JarFile = fs
                .readFileSync(job.outputPath)
                .toString("base64");
            const response = {
                message: "Obfuscation completed successfully!",
                request_id: requestId,
                status: job.status,
                output_file: base64JarFile,
            };

            // Cleanup after successful delivery
            cleanupJob(requestId);

            res.status(200).json(response);
            return;
        } else if (job.status === "failed") {
            const err = new Error(job.error || "Obfuscation failed");
            (err as any).status = 500;
            cleanupJob(requestId);
            return next(err);
        }

        // Job is still processing
        res.status(200).json({
            message: "Job is still processing",
            request_id: requestId,
            status: job.status,
        });
    }
);

router.get("/", (_req: Request, res: Response) => {
    res.status(200).json({ message: "Obfuscation API is ready." });
});

export default router;
