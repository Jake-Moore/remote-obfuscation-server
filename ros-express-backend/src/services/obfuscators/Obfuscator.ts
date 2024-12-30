import { Request, Response, NextFunction } from "express";

export class Obfuscator {
    async obfuscate(
        _req: Request,
        _res: Response,
        _next: NextFunction,
        _jarFile: Express.Multer.File,
        _configFile: Express.Multer.File,
        _requestID: string
    ): Promise<any | void> {
        throw new Error("Method 'obfuscate()' is not implemented.");
    }

    async processStacktrace(
        _req: Request,
        _res: Response,
        _next: NextFunction,
        _request_id: string,
        _stack_trace_base64: string
    ): Promise<any | void> {
        throw new Error("Method 'processStacktrace()' is not implemented.");
    }

    async injectWatermark(
        _req: Request,
        _res: Response,
        _next: NextFunction,
        _jarPath: string,
        _requestID: string,
        _userEmail: string
    ): Promise<any | void> {
        throw new Error("Method 'injectWatermark()' is not implemented.");
    }

    async extractWatermark(
        _req: Request,
        _res: Response,
        _next: NextFunction,
        _jarPath: string
    ): Promise<string> {
        throw new Error("Method 'extractWatermark()' is not implemented.");
    }

    async isJarWatermarked(
        _req: Request,
        _res: Response,
        _next: NextFunction,
        _jarPath: string
    ): Promise<boolean> {
        try {
            await this.extractWatermark(_req, _res, _next, _jarPath);
            // If extractWatermark returned, then we have a watermark
            return true;
        } catch (error) {
            return false;
        }
    }

    generateRandomString(length: number = 50): string {
        const chars =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        let result = "";
        while (result.length < length) {
            const index = Math.floor(Math.random() * chars.length);
            if (index >= 0 && index < chars.length) {
                result += chars.charAt(index);
            }
        }
        return result;
    }
}
