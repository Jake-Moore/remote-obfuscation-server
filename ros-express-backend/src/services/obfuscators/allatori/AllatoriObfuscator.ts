import path from "path";
import { Request, Response, NextFunction } from "express";
import { updateObfConfig } from "./AllatoriConfigGenerator.js";
import {
    getLogsStorageDir,
    getObfuscatorPath,
    getDefaultObfuscatorPath,
    getObfuscatorPathEnvVar,
} from "../../envService.js";
import deleteTemp from "../../ioService.js";
import { getUserEmail } from "../../../middleware/authorization.js";
import fs from "fs";
import colors from "colors";
import { exec } from "child_process";
import AdmZip from "adm-zip";
import { Obfuscator } from "../Obfuscator.js";

export class AllatoriObfuscator extends Obfuscator {
    private watermarkFileName = "obfuscation.json";

    async obfuscate(
        req: Request,
        res: Response,
        next: NextFunction,
        jarFile: Express.Multer.File,
        configFile: Express.Multer.File,
        requestID: string
    ): Promise<void> {
        // Validate file extensions
        if (path.extname(jarFile.originalname) !== ".jar") {
            const err = new Error(
                `The provided 'jar' field file must be a jar file.`
            );
            (err as any).status = 400; // Bad Request
            throw err;
        }
        if (path.extname(configFile.originalname) !== ".xml") {
            const err = new Error(
                `The provided 'config' field file must be an xml file.`
            );
            (err as any).status = 400; // Bad Request
            throw err;
        }

        // Fetch the Obfuscator File Path
        const obfuscatorPath = getObfuscatorPath();
        if (obfuscatorPath === null) {
            const err = new Error(
                "Internal Server Error: Please Check Server Logs."
            );
            (err as any).status = 500; // Internal Server Error
            console.log(
                colors.red(
                    `Invalid obfuscator path: '${
                        process.env[getObfuscatorPathEnvVar()]
                    }'. Override environment variable '${getObfuscatorPathEnvVar()}' or fulfill the default path: '${getDefaultObfuscatorPath()}'`
                )
            );
            throw err;
        }

        // Fetch the user email
        const authHeader = req.headers.authorization || "";
        const token = authHeader.split(" ")[1];
        const userEmail = await getUserEmail(token);
        if (!userEmail) {
            const err = new Error("Failed to fetch user email.");
            (err as any).status = 500; // Internal Server Error
            console.log(colors.red(err.message));
            throw err;
        }

        // Calculate paths (absolute) for obfuscation config
        const inputPath = path.resolve(jarFile.path);
        const configPath = path.resolve(configFile.path);
        const outputPath = path.resolve(
            path.dirname(jarFile.path),
            `${requestID}.jar`
        );
        const logPath = path.resolve(
            path.dirname(jarFile.path),
            `${requestID}.log`
        );
        await updateObfConfig(
            configPath,
            inputPath,
            outputPath,
            logPath,
            requestID,
            userEmail,
            this
        );

        try {
            const output = await this.runAllatoriObfuscate(
                obfuscatorPath,
                configPath
            );
            await this.injectWatermark(
                req,
                res,
                next,
                outputPath,
                requestID,
                userEmail
            );

            // Copy log file to storage
            const logDest = path.resolve(
                getLogsStorageDir(),
                `${requestID}.log`
            );
            fs.copyFileSync(logPath, logDest);
            console.log(
                `Log file for request ${requestID} saved to: '${logDest}'`
            );
            deleteTemp({ path: logPath });

            // Return without base64 encoding the jar - that will happen when the client polls for it
            return;
        } catch (error) {
            console.error(colors.red(error as any));
            throw new Error(`Failed to obfuscate jar file: ${error}`);
        }
    }

    private runAllatoriObfuscate(
        allatoriPath: string,
        configPath: string
    ): Promise<string> {
        return new Promise((resolve, reject) => {
            const command = `java -cp "${allatoriPath}" com.allatori.Obfuscate "${configPath}"`;

            exec(command, (error, stdout, stderr) => {
                if (error) {
                    console.log(colors.red(`Error: ${stdout}`));
                    return reject(`Error: ${stderr || error.message}`);
                }
                resolve(stdout);
            });
        });
    }

    async processStacktrace(
        _req: Request,
        res: Response,
        next: NextFunction,
        requestID: string,
        stackTraceBase64: string
    ): Promise<void> {
        const decodedStackTrace = Buffer.from(
            stackTraceBase64,
            "base64"
        ).toString("utf-8");

        const logStorageDir = getLogsStorageDir();
        const logPath = `${logStorageDir}/${requestID}.log`;
        if (!fs.existsSync(logPath)) {
            const err = new Error(
                `No log file found for requestID: ${requestID}`
            );
            (err as any).status = 404;
            return next(err);
        }

        const obfuscatorPath = getObfuscatorPath();
        if (obfuscatorPath === null) {
            const err = new Error(
                `Invalid obfuscator path: '${
                    process.env[getObfuscatorPathEnvVar()]
                }'. Override environment variable '${getObfuscatorPathEnvVar()}' or fulfill the default path: '${getDefaultObfuscatorPath()}'`
            );
            (err as any).status = 500;
            console.log(colors.red(err.message));
            return next(err);
        }

        const uploadsDir = path.resolve(
            process.env.ROS_UPLOADS_TRACE_STORAGE || "./uploads-trace"
        );
        const tracePath = `${uploadsDir}/${requestID}.log`;
        const tracePathOut = `${uploadsDir}/${requestID}-out.log`;
        fs.mkdirSync(uploadsDir, { recursive: true });
        fs.writeFileSync(tracePath, decodedStackTrace);

        try {
            await this.runAllatoriTrace(
                obfuscatorPath,
                logPath,
                tracePath,
                tracePathOut
            );

            const traceOutStr = fs.readFileSync(tracePathOut, "utf-8");
            const traceOutBase64 = Buffer.from(traceOutStr).toString("base64");
            res.status(200).json({
                message: "Stack trace translated successfully",
                request_id: requestID,
                output_trace_base64: traceOutBase64,
            });
        } catch (error) {
            // Pass errors to the error handler middleware
            console.error(colors.red(`Error calling stacktrace: ${error}`));
            const err = new Error(
                "Error calling stacktrace. Please check the server logs."
            );
            (err as any).status = 500;
            return next(err);
        } finally {
            deleteTemp({ path: tracePath });
            deleteTemp({ path: tracePathOut });
        }
    }

    private runAllatoriTrace(
        allatoriPath: string,
        logPath: string,
        tracePath: string,
        tracePathOut: string
    ): Promise<string> {
        return new Promise((resolve, reject) => {
            const command = `java -Xms128m -Xmx512m -cp "${allatoriPath}" com.allatori.StackTrace2 "${logPath}" "${tracePath}" "${tracePathOut}"`;

            exec(command, (error, stdout, stderr) => {
                if (error) {
                    console.log(colors.red(`Error: ${stdout}`));
                    return reject(`Error: ${stderr || error.message}`);
                }
                resolve(stdout);
            });
        });
    }

    async injectWatermark(
        _req: Request,
        _res: Response,
        next: NextFunction,
        jarPath: string,
        requestID: string,
        userEmail: string
    ): Promise<void> {
        try {
            const zip = new AdmZip(jarPath);
            const obfData = { request_id: requestID, request_user: userEmail };
            zip.addFile(
                this.watermarkFileName,
                Buffer.from(JSON.stringify(obfData))
            );
            zip.writeZip(jarPath);
        } catch (error) {
            const err = new Error(
                `Failed to inject watermark into JAR file: ${error}`
            );
            (err as any).status = 500;
            return next(err);
        }
    }

    /**
     * @throws {Error} If watermark file is not found, or file fails to parse.
     */
    async extractWatermark(
        _req: Request,
        _res: Response,
        next: NextFunction,
        jarPath: string
    ): Promise<any> {
        // We don't catch any errors here, we want to pass them to the calling function
        const zip = new AdmZip(jarPath);
        const obfFile = zip.getEntry(this.watermarkFileName);

        if (!obfFile) {
            throw new Error("Failed to find watermark file in JAR.");
        }

        const obfData = obfFile.getData().toString("utf8");
        return JSON.parse(obfData);
    }
}
