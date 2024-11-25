import express, { Request, Response, NextFunction } from "express";
import multer from "multer";
import colors from "colors";
// Service Methods
import { Obfuscator } from "../services/obfuscators/Obfuscator.js";
import { getObfuscator } from "../services/obfuscators/ObfuscatorLoader.js";
import deleteTemp from "../services/ioService.js";

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
    async (req: Request, res: Response, next: NextFunction) => {
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

            // Attempt obfuscation on the provided files
            return await obfuscator.obfuscate(
                req,
                res,
                next,
                jarFile,
                configFile
            );
        } catch (error) {
            // Pass errors to the error handler middleware
            console.error(colors.red(`Error calling obfuscate: ${error}`));
            const err = new Error(
                "Error calling obfuscation. Please check the server logs."
            );
            (err as any).status = 500;
            return next(err);
        } finally {
            // Ensure we delete the temporary files
            deleteTemp(jarFile);
            deleteTemp(configFile);
        }
    }
);

router.get("/", (req: Request, res: Response) => {
    res.status(200).json({ message: "Obfuscation API is ready." });
});

export default router;
