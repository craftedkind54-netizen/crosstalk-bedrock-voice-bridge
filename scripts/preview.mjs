import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
const root = path.resolve(fileURLToPath(new URL("../core/web/", import.meta.url)));
const types = { ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".css": "text/css", ".png": "image/png", ".svg": "image/svg+xml" };
http.createServer(async (req, res) => {
    try {
        const pathname = decodeURIComponent(new URL(req.url, "http://localhost").pathname);
        if (pathname === "/ws") { res.writeHead(503); res.end("Preview only. Install the Paper plugin for voice connections."); return; }
        const file = path.resolve(root, "." + (pathname === "/" ? "/index.html" : pathname));
        if (!file.startsWith(root + path.sep) && file !== path.join(root, "index.html")) throw new Error("Invalid path");
        const bytes = await readFile(file);
        res.writeHead(200, { "Content-Type": types[path.extname(file)] || "application/octet-stream", "Cache-Control": "no-store" });
        res.end(bytes);
    } catch { res.writeHead(404); res.end("Not found"); }
}).listen(8794, "127.0.0.1", () => console.log("CrossTalk design preview: http://127.0.0.1:8794 (no Minecraft server attached)"));
