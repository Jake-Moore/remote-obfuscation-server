import { getObfuscatorType } from "../envService.js";
import { AllatoriObfuscator } from "./allatori/AllatoriObfuscator.js";
import { Obfuscator } from "./Obfuscator.js";

export function getObfuscator(): Obfuscator {
    const obfType = getObfuscatorType();
    switch (obfType.toUpperCase()) {
        case "ALLATORI":
            return new AllatoriObfuscator();
        default:
            throw new Error(`Unknown obfuscator type: ${obfType}`);
    }
}
