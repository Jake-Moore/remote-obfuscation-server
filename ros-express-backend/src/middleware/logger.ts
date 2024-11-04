import colors, { Color } from 'colors';
import { Request, Response, NextFunction } from 'express';

const logger = (req: Request, _res: Response, next: NextFunction): void => {
    const methodColors: { [key: string]: Color } = {
        GET: colors.green,
        POST: colors.yellow,
        PUT: colors.blue,
        DELETE: colors.red,
    };

    const color = methodColors[req.method as keyof typeof methodColors] || colors.white;

    // Use the color function directly to format the log message
    console.log(color(`${req.method} ${req.protocol}://${req.get('host')}${req.originalUrl}`));
    
    next();
}

export default logger;
