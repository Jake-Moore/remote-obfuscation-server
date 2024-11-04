import express from 'express';

// Middleware
import logger from './middleware/logger.js';
import errorHandler from './middleware/error.js';
import notFound from './middleware/notFound.js';
import validateAuthorization from './middleware/authorization.js';
// Routes
import obfuscateRoute from './routes/obfuscateRoute.js';
import watermarkRoute from './routes/watermarkRoute.js';
import stacktraceRoute from './routes/stacktraceRoute.js';
// Services
import { getRequiredGitOrgs } from './services/envService.js';
import colors from 'colors';

const app = express();

// Configure Proxy Trusting for Reverse Proxies
// (so that ips resolve properly)
const reverseProxied: boolean = process.env.ROS_REVERSE_PROXY === 'true';
if (reverseProxied) {
    console.log(colors.gray('Enabling Reverse Proxy Support - Trusting Proxy'));
    app.set('trust proxy', true);
}

// Middleware (Body Parser)
app.use(express.json());
app.use(express.urlencoded({ extended: false }));
// Middleware (Logger)
app.use(logger);
app.use(validateAuthorization);

// Routes
app.use('/api/obfuscate', obfuscateRoute);
app.use('/api/watermark', watermarkRoute);
app.use('/api/stacktrace', stacktraceRoute);
app.use(notFound);

// Below Routes (!)
app.use(errorHandler);

// Pull Port from Environment variables
const port: number = parseInt(process.env.ROS_PORT || '3000');

// Check authorized orgs for a helpful warning if not set
const requiredOrgs = getRequiredGitOrgs();
if (requiredOrgs.length === 0) {
    console.log(colors.yellow('------------------------------'));
    console.log(colors.yellow('Warning: No required organizations set. All requests will be denied.'));
    console.log(colors.yellow('Please set the ROS_REQUIRED_ORGS environment variable to a comma-delimited list of required organization names.'));
    console.log(colors.yellow('------------------------------'));
} else {
    console.log(`Authorized organizations: ${requiredOrgs.join(', ')}`);
}

const ipv4Only: boolean = process.env.ROS_IPV4_ONLY === 'true';
if (ipv4Only) {
    // Start the server in HTTP mode (assume reverse proxy is handling SSL)
    console.log(colors.gray('IPv4 Only Mode Enabled'));
    app.listen(port, "0.0.0.0", () => {
        console.log(colors.green(`Server is running (http) on port ${port}`));
    });
}else {
    // Start the server in HTTP mode (assume reverse proxy is handling SSL)
    app.listen(port, () => {
        console.log(colors.green(`Server is running (http) on port ${port}`));
    });
}