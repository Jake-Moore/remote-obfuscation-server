import fs from 'fs';

export function getRequiredGitOrgs(): string[] {
  const requiredOrgsStr: string = process.env.ROS_REQUIRED_ORGS || '';
  const requiredOrgs: string[] = requiredOrgsStr
    .split(',')
    .map(org => org.trim())
    .filter(org => org.length > 0);
  return requiredOrgs;
}

export function getLogsStorageDir(): string {
  if (!process.env.ROS_LOG_STORAGE || process.env.ROS_LOG_STORAGE === '') {
    return '/var/log/ros';
  }
  return process.env.ROS_LOG_STORAGE;
}

export function getDefaultObfuscatorPath(): string {
  return '/obfuscator/myObfuscator.jar';
}

export function getObfuscatorPathEnvVar(): string {
  return 'ROS_OBFUSCATOR_PATH';
}

export function getObfuscatorPath(): string | null {
  // Check if our obfuscator path is a valid file
  const obfuscatorPath = process.env[getObfuscatorPathEnvVar()] || getDefaultObfuscatorPath();
  if (!fs.existsSync(obfuscatorPath) || !fs.lstatSync(obfuscatorPath).isFile()) {
      return null;
  }
  return obfuscatorPath;
}

export function getObfuscatorTypeEnvVar(): string {
  return 'ROS_OBFUSCATOR_TYPE';
}

export function getObfuscatorType(): string {
  const obfType: string | null = process.env[getObfuscatorTypeEnvVar()] || null;
  if (obfType === null) {
    throw new Error(`${getObfuscatorTypeEnvVar()} is not set!`);
  }
  return obfType;
}
