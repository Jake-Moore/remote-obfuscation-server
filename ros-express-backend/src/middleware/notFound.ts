import { Request, Response, NextFunction } from "express";

const notFound = (_req: Request, _res: Response, next: NextFunction): void => {
    const error = new Error("Not Found");
    (error as any).status = 404; // Setting the status property
    return next(error);
};

export default notFound;
