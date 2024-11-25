import express, { Request, Response, NextFunction } from "express";
import colors from "colors";
// Services
import { Obfuscator } from "../services/obfuscators/Obfuscator.js";
import { getObfuscator } from "../services/obfuscators/ObfuscatorLoader.js";

const router = express.Router();

// Express Endpoint (POST)
router.post("/", async (req: Request, res: Response, next: NextFunction) => {
    try {
        // Extracting requestID and base64Data from the JSON body
        const { request_id, stack_trace_base64 } = req.body;

        // Check if both fields exist, and that they are strings
        if (
            !request_id ||
            !stack_trace_base64 ||
            typeof request_id !== "string" ||
            typeof stack_trace_base64 !== "string"
        ) {
            return res
                .status(400)
                .json({ error: "request_id and base64Data are required." });
        }

        // Obtain an obfuscator instance
        const obfuscator: Obfuscator = getObfuscator();

        return await obfuscator.processStacktrace(
            req,
            res,
            next,
            request_id,
            stack_trace_base64
        );
    } catch (error) {
        console.error(colors.red(`Error processing request: ${error}`));
        const err = new Error(
            "Error calling obfuscation. Please check the server logs."
        );
        (err as any).status = 500;
        return next(err);
    }
});

// Simple GET endpoint
router.get("/", (req: Request, res: Response) => {
    res.status(200).json({ message: "Stack Trace API is ready." });
});

export default router;
