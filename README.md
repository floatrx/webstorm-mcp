# WebStorm MCP Bridge

Bridge between WebStorm/JetBrains IDEs and Claude Code via MCP (Model Context Protocol).

**Problem:** Claude Code running in terminal cannot see what you have selected in your IDE.

**Solution:** This project provides a WebStorm plugin + MCP server that lets Claude Code access your editor selection in real-time.

## Demo

```
You: "What's selected in my IDE?"
Claude: [Uses get_ide_selection tool]
Claude: "You have selected a TypeScript function at src/utils/helper.ts:45-52..."
```

## Architecture

```
┌─────────────┐     HTTP      ┌─────────────┐     MCP      ┌─────────────┐
│  WebStorm   │ ──────────►   │  MCP Server │ ──────────►  │ Claude Code │
│  Plugin     │  :63343       │  (Node.js)  │   stdio      │             │
└─────────────┘               └─────────────┘              └─────────────┘
```

## Components

| Component | Description |
|-----------|-------------|
| `plugin/` | WebStorm plugin - exposes selection via HTTP |
| `mcp-server/` | MCP server - provides tools for Claude Code |

## Quick Start

### 1. Build & Install Plugin

```bash
cd plugin
export JAVA_HOME=/opt/homebrew/opt/openjdk@21  # macOS with Homebrew
./gradlew buildPlugin
```

Install `build/distributions/webstorm-ide-bridge-1.0.0.zip` in WebStorm:
- **Settings** → **Plugins** → ⚙️ → **Install Plugin from Disk...**

### 2. Build MCP Server

```bash
cd mcp-server
pnpm install
pnpm build
```

### 3. Configure Claude Code

Add to `~/.claude/.mcp.json`:

```json
{
  "mcpServers": {
    "webstorm-bridge": {
      "command": "node",
      "args": ["/absolute/path/to/webstorm-mcp/mcp-server/dist/index.js"]
    }
  }
}
```

### 4. Restart Both

1. Restart WebStorm (plugin loads on startup)
2. Restart Claude Code (MCP server loads on startup)

### 5. Use It

Select code in WebStorm, then ask Claude:
- "What do I have selected in my IDE?"
- "Explain this code" (while having code selected)
- "Refactor the selected function"

## MCP Tools

| Tool | Description |
|------|-------------|
| `get_ide_selection` | Get selected text with file path, line numbers, language |
| `get_ide_context` | Get cursor position and file info (even without selection) |

## API Reference

### GET http://localhost:63343/api/selection

```json
{
  "text": "function foo() { return 42; }",
  "filePath": "/Users/you/project/src/index.ts",
  "startLine": 10,
  "endLine": 12,
  "startColumn": 1,
  "endColumn": 2,
  "language": "TypeScript",
  "projectName": "my-project"
}
```

### GET http://localhost:63343/api/health

```json
{
  "status": "ok",
  "plugin": "ide-bridge",
  "version": "1.0.0"
}
```

## Requirements

- **WebStorm/IntelliJ:** 2025.1+
- **Java:** 21+ (for building plugin)
- **Node.js:** 20+ (for MCP server)
- **Claude Code:** Latest version with MCP support

## Troubleshooting

### Test plugin is running

```bash
curl http://localhost:63343/api/health
# Should return: {"status":"ok","plugin":"ide-bridge","version":"1.0.0"}
```

### Test selection endpoint

```bash
curl http://localhost:63343/api/selection
# Returns current selection or empty response
```

### Check WebStorm logs

**Help** → **Show Log in Finder** → search for "IDE Bridge"

## License

MIT

## Author

[@floatrx](https://github.com/floatrx)
