import express, { Request, Response, NextFunction } from 'express';
import multer from 'multer';
import colors from 'colors';
// Services
import { Obfuscator } from '../services/obfuscators/Obfuscator.js';
import { getObfuscator } from '../services/obfuscators/ObfuscatorLoader.js'
import deleteTemp from '../services/ioService.js';

const router = express.Router();

// Configure multer to handle file uploads
const upload = multer({ dest: (process.env.ROS_UPLOADS_WATERMARK_STORAGE || 'uploads-watermark/') });

// Define a type for the expected files structure
interface RequestFiles {
    jar?: Express.Multer.File[];
}

// Express Endpoint (POST)
router.post(
    '/',
    upload.fields([{ name: 'jar' }]),
    async (req: Request, res: Response, next: NextFunction): Promise<any> => {
        const files = req.files as RequestFiles;

        // Check if the jar file is provided
        if (!files || !files.jar) {
            const err = new Error(`You must provide a 'jar' file field (types: jar).`);
            (err as any).status = 400; // Bad Request
            return next(err);
        }

        // Parse files from request
        const jarFile = files.jar[0];

        // Obtain an obfuscator instance
        const obfuscator: Obfuscator = getObfuscator();

        try {
            // Attempt watermark extraction on the provided file
            const data = await obfuscator.extractWatermark(req, res, next, jarFile.path);
            return res.status(200).json(data);
        } catch (error) {
            // Pass errors to the error handler middleware
            console.error(colors.red('Error calling watermark: ${error}'));
            const err = new Error('Error calling watermark. Please check the server logs.');
            (err as any).status = 500;
            return next(err);
        } finally {
            // Ensure we delete the temporary files
            deleteTemp(jarFile);
        }
    }
);

// Simple GET endpoint
router.get('/', (req: Request, res: Response) => {
    res.status(200).json({ message: 'Watermark API is ready.' });
});

export default router;
