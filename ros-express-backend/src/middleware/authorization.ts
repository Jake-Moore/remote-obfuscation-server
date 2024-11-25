import colors from "colors";
import axios from "axios";
import { getRequiredGitOrgs } from "../services/envService.js";
import { Request, Response, NextFunction } from "express";

export default async function validateAuthorization(
    req: Request,
    res: Response,
    next: NextFunction
) {
    // Configure Allowed Public Routes
    const publicRoutes: { [key: string]: string } = {
        "/api/obfuscate": "GET",
        "/api/watermark": "GET",
        "/api/stacktrace": "GET",
        "/favicon.ico": "GET",
    };
    if (publicRoutes[req.originalUrl] === req.method) {
        return next();
    }

    // If no token is provided, return a 401 status
    if (!req.headers.authorization) {
        const err = new Error(`Authorization Token Required.`);
        (err as any).status = 401;
        console.log(
            colors.red(`[AUTH] Request denied from ${req.ip} - ${err.message}`)
        );
        return next(err);
    }

    // Use provided token to get user's Git organizations
    const token = req.headers.authorization.split(" ")[1];

    // Fetch the Supplemental Allowed PATs
    const supplementalPATsVal = process.env.ROS_SUPPLEMENTAL_PATS || "";
    const supplementalPATs = supplementalPATsVal
        .split(",")
        .map((pat) => pat.trim())
        .filter((pat) => pat.length > 0);
    if (supplementalPATs.includes(token)) {
        // The token is a supplemental PAT which is authorized immediately
        console.log(
            colors.green(
                `[AUTH] Request authorized from ${req.ip} - Supplemental PAT`
            )
        );
        return next();
    }

    // Sanity check - Does the token look like a GitHub token?
    // Format: 'ghp_XXX' where XXX is an alphanumeric string
    const tokenRegex = /^ghp_[a-zA-Z0-9]+$/;
    if (!tokenRegex.test(token)) {
        const err = new Error(`Unauthorized: Invalid token format!`);
        (err as any).status = 401;
        console.log(
            colors.red(
                `[AUTH] Request denied from ${req.ip} - ${err.message} - '${token}'`
            )
        );
        return next(err);
    }

    try {
        const response = await axios.get("https://api.github.com/user/orgs", {
            headers: {
                Authorization: `token ${token}`,
            },
        });

        // Validate the response data is a JSON array
        if (!Array.isArray(response.data)) {
            const err = new Error(`Error authorizing request.`);
            (err as any).status = 401;
            console.log(
                colors.red(
                    `[AUTH] Request denied from ${req.ip} - ${err.message} - Malformed response data`
                )
            );
            return next(err);
        }

        // Compose an array of this user's orgs from the 'login' field in each object of the array
        const organizations = response.data
            .map((org: { login: string }) => org.login || "")
            .filter((org) => org.length > 0);
        const requiredOrgs = getRequiredGitOrgs();

        // Require that we are able to fetch an email for this user
        const userEmail = await getUserEmail(token);

        // Require presence in all of the required orgs
        if (!requiredOrgs.every((org) => organizations.includes(org))) {
            // Deny the request
            const err = new Error(`Unauthorized: Not in required orgs`);
            (err as any).status = 401;
            console.log(
                colors.red(
                    `[AUTH] Request denied from ${req.ip} - ${err.message}`
                )
            );
            return next(err);
        }

        console.log(
            colors.green(
                `[AUTH] Request authorized from ${req.ip} - ${userEmail}`
            )
        );
        return next();
    } catch (error) {
        console.log(
            colors.red(
                `[AUTH] Request denied from ${req.ip} - ${
                    (error as Error).message
                }`
            )
        );
        res.status(401).json({ message: "Error authorizing with token." });
    }
}

export async function getUserEmail(token: string): Promise<string> {
    // Fetch the Supplemental Allowed PATs
    const supplementalPATsVal = process.env.ROS_SUPPLEMENTAL_PATS || "";
    const supplementalPATs = supplementalPATsVal
        .split(",")
        .map((pat) => pat.trim())
        .filter((pat) => pat.length > 0);
    if (supplementalPATs.includes(token)) {
        // This token is authorized as a supplemental PAT
        return "supplemental@ros.pat";
    }

    const response = await axios.get("https://api.github.com/user/emails", {
        headers: {
            Authorization: `token ${token}`,
        },
    });

    // Validate the response data is a JSON array
    if (!Array.isArray(response.data)) {
        throw new Error(`Error: Malformed email response data`);
    }

    // Find the primary email for this user
    const primary = response.data.find(
        (email: { primary: boolean; email: string }) => email.primary
    );
    if (!primary || !primary.email) {
        throw new Error(`Error: No primary user email found`);
    }
    return primary.email;
}
