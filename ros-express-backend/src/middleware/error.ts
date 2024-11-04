import { Request, Response, NextFunction } from 'express';

const errorHandler = (err: { status?: number; message?: string }, _req: Request, res: Response, _next: NextFunction): void => {
    // Default to Code 500 if not specified
    const statusCode = err.status || 500;
    const errorMessage = err.message || 'Internal Server Error';

    res.status(statusCode).json({
        error: {
            code: statusCode.toString(),
            message: errorMessage,
        },
    });
};

export default errorHandler;
