# IDE Bridge for Claude

A WebStorm/IntelliJ plugin that exposes editor selection via HTTP endpoint, enabling Claude Code to access your IDE context through MCP (Model Context Protocol).

## Purpose

When using Claude Code in a terminal, it cannot see what you have selected in your IDE. This plugin bridges that gap by:

1. Running a local HTTP server inside WebStorm (port 63343)
2. Exposing your current editor selection via REST API
3. Allowing Claude Code's MCP server to fetch and display your selection

## How It Works

```
┌─────────────┐     HTTP      ┌─────────────┐     MCP      ┌─────────────┐
│  WebStorm   │ ──────────►   │  MCP Server │ ──────────►  │ Claude Code │
│  Plugin     │  :63343       │  (Node.js)  │   stdio      │             │
└─────────────┘               └─────────────┘              └─────────────┘
```

## Installation

### From Release (Recommended)

1. Download `webstorm-ide-bridge-1.0.0.zip` from [Releases](https://github.com/floatrx/webstorm-mcp/releases)
2. Open WebStorm → **Settings** → **Plugins**
3. Click ⚙️ → **Install Plugin from Disk...**
4. Select the downloaded ZIP file
5. Restart WebStorm

### Build from Source

```bash
# Requires Java 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Build plugin
./gradlew buildPlugin

# Plugin ZIP will be at:
# build/distributions/webstorm-ide-bridge-1.0.0.zip
```

## API Endpoints

### GET /api/selection

Returns the current editor selection:

```json
{
  "text": "const foo = 'bar';",
  "filePath": "/path/to/file.ts",
  "startLine": 10,
  "endLine": 10,
  "startColumn": 1,
  "endColumn": 19,
  "language": "TypeScript",
  "projectName": "my-project"
}
```

### GET /api/health

Health check endpoint:

```json
{
  "status": "ok",
  "plugin": "ide-bridge",
  "version": "1.0.0"
}
```

## Usage with Claude Code

### 1. Install MCP Server

```bash
cd ../mcp-server
pnpm install
pnpm build
```

### 2. Configure Claude Code

Add to `~/.claude/.mcp.json`:

```json
{
  "mcpServers": {
    "webstorm-bridge": {
      "command": "node",
      "args": ["/path/to/webstorm-mcp/mcp-server/dist/index.js"]
    }
  }
}
```

### 3. Restart Claude Code

### 4. Use in Conversation

Select code in WebStorm, then ask Claude:
- "What do I have selected?"
- "Explain this selection"
- "Refactor the selected code"

Claude will use the `get_ide_selection` tool to fetch your selection.

## Compatibility

- **WebStorm:** 2025.1+
- **IntelliJ IDEA:** 2025.1+
- **Other JetBrains IDEs:** Should work with any 2025.1+ IDE

## Troubleshooting

### Plugin not loading

Check WebStorm logs: **Help** → **Show Log in Finder**

Look for: `IDE Bridge: Server started on http://127.0.0.1:63343`

### Port already in use

The plugin uses port 63343 (JetBrains built-in server port range). If another service uses this port, you'll need to modify `SelectionServer.kt` and rebuild.

### Connection refused

Make sure:
1. WebStorm is running
2. Plugin is installed and enabled
3. Test with: `curl http://localhost:63343/api/health`

## License

MIT
