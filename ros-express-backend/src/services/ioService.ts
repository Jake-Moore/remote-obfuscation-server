import fs from 'fs';
import colors from 'colors';

interface File {
    path: string;
}

// Helper Method to delete the temporary upload file
export default function deleteTemp(file: File): void {
    fs.unlink(file.path, (err) => {
        // Gracefully handle file not found
        if (err && err.code === 'ENOENT') {
            console.log(colors.gray("Temporary file does not exist, no need to delete"));
            return;
        }

        if (err) {
            console.error(colors.yellow(`Error deleting temporary file: ${file.path}`));
        } else {
            // console.log(colors.gray(`Deleted temporary file: ${file.path}`));
        }
    });
}
