import fs from 'fs';
import { XMLParser, XMLBuilder } from 'fast-xml-parser';
import crypto from 'crypto';
import { Obfuscator } from '../Obfuscator.js';

export function generateUUIDFragment(): string {
    // Generate a UUID
    const uuid = crypto.randomUUID();
    // Split the UUID by the hyphen and return the first part
    return uuid.split('-')[0];
}

export async function updateObfConfig(
    configPath: string,
    inputPath: string,
    outputPath: string,
    logPath: string,
    requestID: string,
    _userEmail: string,
    obfuscator: Obfuscator
): Promise<void> {
    // Load and parse the XML file
    const xmlData = fs.readFileSync(configPath, 'utf8');
    const parser = new XMLParser({ ignoreAttributes: false });
    let xml = parser.parse(xmlData);

    // Validate that the <config> root exists
    if (!xml.config) {
        throw new Error("Invalid XML: <config> root element is missing.");
    }

    // Ensure <input><jar> structure exists and set 'in' and 'out' attributes
    xml.config.input = {};
    xml.config.input.jar = {};

    // Set the 'in' and 'out' attributes for the <jar> element with safe path notation
    xml.config.input.jar["@_in"] = inputPath;
    xml.config.input.jar["@_out"] = outputPath;

    // Store the log file path in the correct property
    if (!xml.config.property) {
        xml.config.property = [];
    }
    // Remove any existing log-file property
    xml.config.property = xml.config.property.filter((prop: any) => prop["@_name"] !== "log-file");
    // Add the log file property
    xml.config.property.push({
        "@_name": "log-file",
        "@_value": logPath
    });

    // Remove any watermark keys (breaks the obfuscation process at times)
    delete xml.config.watermark;

    // Delete any existing random-seed property
    xml.config.property = xml.config.property.filter((prop: any) => prop["@_name"] !== "random-seed");
    // Set the random seed to a random 50-character string
    const seed = obfuscator.generateRandomString(50);
    xml.config.property.push({
        "@_name": "random-seed",
        "@_value": seed
    });

    // Convert the modified object back to XML
    const builder = new XMLBuilder({
        ignoreAttributes: false,
        format: true,
        indentBy: "    "
    });
    const updatedXml = builder.build(xml);

    // Write the updated XML back to the file
    fs.writeFileSync(configPath, updatedXml);
    // console.log(colors.green(`Updated XML file: ${configPath}`));
}

export async function generateWatermarkConfig(configPath: string, inputPath: string): Promise<void> {
    // Create an empty xml object, which will be filled with the required data
    let xml: any = {}

    // Ensure the <config> root exists
    if (!xml.config) {
        xml.config = {};
    }

    // Ensure the <input> section exists and add <jar> entries
    if (!xml.config.input) {
        xml.config.input = { jar: [] };
    }
    // Add the required <jar> elements with 'in' attributes
    xml.config.input.jar.push({ "@_in": inputPath });

    // Convert the modified object back to XML
    const builder = new XMLBuilder({
        ignoreAttributes: false,
        format: true,
        indentBy: "    "
    });
    const updatedXml = builder.build(xml);

    // Write the updated XML back to the file
    fs.writeFileSync(configPath, updatedXml);
    // console.log(colors.green(`Updated XML file: ${configPath}`));
}
