import colors from "colors";
import axios from "axios";
import NodeCache from "node-cache";
import { getRequiredGitOrgs } from "../services/envService.js";
import { Request, Response, NextFunction } from "express";

// Define a new interface for user information
export interface UserInfo {
    email: string;
    username: string;
}

// Define a new interface for supplemental PATs
export interface SupplementalPAT {
    token: string;
    nickname: string | null;
}

// Initialize cache with 1 minute TTL
const cache = new NodeCache({ stdTTL: 60 });

// Function to load supplemental PATs from environment variables
function loadSupplementalPATs(): SupplementalPAT[] {
    const supplementalPATsVal = process.env.ROS_SUPPLEMENTAL_PATS || "";
    const stems = supplementalPATsVal
        .split(",")
        .map((stem) => stem.trim())
        .filter((stem) => stem.length > 0);
    
    const pats: SupplementalPAT[] = [];
    
    for (const stem of stems) {
        const patEnvVar = `ROS_SUPPLEMENTAL_${stem}`;
        const patNameEnvVar = `ROS_SUPPLEMENTAL_${stem}_NAME`;
        
        const token = process.env[patEnvVar];
        if (token) {
            pats.push({
                token,
                nickname: process.env[patNameEnvVar] || null
            });
        } else {
            console.log(colors.yellow(`[AUTH] Warning: Supplemental PAT environment variable ${patEnvVar} not found`));
        }
    }
    
    return pats;
}

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

    // Load supplemental PATs
    const supplementalPATs = loadSupplementalPATs();
    const matchedPAT = supplementalPATs.find(pat => pat.token === token);
    
    if (matchedPAT) {
        // The token is a supplemental PAT which is authorized immediately
        console.log(
            colors.green(
                `[AUTH] Request authorized from ${req.ip} - Supplemental PAT: ${matchedPAT.nickname || "unnamed"}`
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
        // Check cache first for organizations
        const cacheKey = `orgs_${token}`;
        const cachedOrgs = cache.get<string[]>(cacheKey);
        
        let organizations: string[];
        if (cachedOrgs) {
            organizations = cachedOrgs;
        } else {
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
            organizations = response.data
                .map((org: { login: string }) => org.login || "")
                .filter((org) => org.length > 0);
            
            // Store in cache
            cache.set(cacheKey, organizations);
        }

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
        console.log(error);
        res.status(401).json({ message: "Error authorizing with token." });
    }
}

export async function getUserInfo(token: string): Promise<UserInfo> {
    // Check for supplemental PATs
    const supplementalPATs = loadSupplementalPATs();
    const matchedPAT = supplementalPATs.find(pat => pat.token === token);
    
    if (matchedPAT) {
        // This token is authorized as a supplemental PAT
        return {
            email: "supplemental@ros.pat",
            username: matchedPAT.nickname || "supplemental-pat"
        };
    }

    // Check cache first for user info
    const cacheKey = `userinfo_${token}`;
    const cachedUserInfo = cache.get<UserInfo>(cacheKey);
    
    if (cachedUserInfo) {
        return cachedUserInfo;
    }

    // Get username and email from GitHub API
    const userResponse = await axios.get("https://api.github.com/user", {
        headers: {
            Authorization: `token ${token}`,
        },
    });

    if (!userResponse.data || !userResponse.data.login) {
        throw new Error(`Error: Could not retrieve GitHub username`);
    }

    const username = userResponse.data.login;
    const email = userResponse.data.email;
    if (!email) {
        throw new Error(`Error: Could not retrieve GitHub email for ${username}`);
    }

    const userInfo: UserInfo = {
        email: email,
        username: username
    };

    // Store in cache
    cache.set(cacheKey, userInfo);
    return userInfo;
}

export async function getUserEmail(token: string): Promise<string> {
    // For backward compatibility, use the new getUserInfo method and return just the email
    const userInfo = await getUserInfo(token);
    return userInfo.email;
}
