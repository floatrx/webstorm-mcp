# WebStorm MCP Bridge

Bridge between WebStorm/JetBrains IDEs and Claude Code via MCP (Model Context Protocol).

## When Do You Need This?

**This MCP is specifically for running Claude Code in tmux/headless mode** — for example, when controlling Claude remotely via Telegram bot or SSH.

> **Note:** If you're running Claude Code directly in your terminal (not in tmux), you don't need this. Instead, use the built-in IDE integration: **Claude Code Settings** → **IDE** → set custom command (e.g., `webstorm` or `ws`).

**Problem:** When Claude Code runs inside tmux, it loses connection to your IDE and cannot see what you have selected.

**Solution:** This project provides a WebStorm plugin + MCP server that lets Claude Code access your editor selection via HTTP, regardless of how Claude is launched.

### Example: Running Claude in tmux

```bash
# Start Claude in a tmux session
tmux new-session -s claude "claude"

# Now you can detach (Ctrl+B, D) and control Claude remotely
# The MCP bridge maintains IDE connection even when detached
```

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

### 1. Install Plugin

> ⚠️ **Important:** This plugin is NOT available in JetBrains Marketplace. Install manually or use custom repository.

**Option A: Custom Plugin Repository (Recommended for Auto-Updates)**

Add our plugin repository to get automatic updates:

1. In WebStorm: **Settings** → **Plugins** → click ⚙️ → **Manage Plugin Repositories...**
2. Click **+** and add:
   ```
   https://floatrx.github.io/webstorm-mcp/updatePlugins.xml
   ```
3. Click **OK**
4. Go to **Marketplace** tab → search "IDE Bridge" → **Install**
5. Restart WebStorm

WebStorm will now check for plugin updates automatically.

**Option B: Download Pre-built**

1. Download `webstorm-ide-bridge-1.1.0.zip` from [Releases](https://github.com/floatrx/webstorm-mcp/releases)
2. In WebStorm: **Settings** → **Plugins** → click ⚙️ → **Install Plugin from Disk...**
3. Select the downloaded `.zip` file (do NOT extract it)
4. Click **OK** and restart WebStorm

**Option C: Build from Source**

```bash
cd plugin
export JAVA_HOME=/opt/homebrew/opt/openjdk@21  # macOS with Homebrew
./gradlew buildPlugin
# Install from: build/distributions/webstorm-ide-bridge-1.1.0.zip
```

### 2. Build MCP Server

```bash
cd mcp-server
pnpm install
pnpm build
```

### 3. Configure Claude Code

Add MCP server to your Claude Code configuration.

**Option A: Global config** (recommended for personal use)

Edit or create `~/.claude/.mcp.json`:

```json
{
  "mcpServers": {
    "webstorm-bridge": {
      "command": "node",
      "args": ["/Users/yourname/path/to/webstorm-mcp/mcp-server/dist/index.js"]
    }
  }
}
```

**Option B: Project config** (for specific projects)

Create `.mcp.json` in your project root:

```json
{
  "mcpServers": {
    "webstorm-bridge": {
      "command": "node",
      "args": ["/Users/yourname/path/to/webstorm-mcp/mcp-server/dist/index.js"]
    }
  }
}
```

> **Important:** Use absolute path to `dist/index.js`. Replace `/Users/yourname/path/to/` with your actual path.

### 4. Restart & Verify

1. **Restart WebStorm** — plugin loads on startup
2. **Restart Claude Code** — MCP server loads on startup

**Verify MCP is loaded:**

```bash
# In Claude Code, run:
/mcp
```

You should see `webstorm-bridge` in the list with 7 tools available.

### 5. Use It

Select code in WebStorm, then ask Claude:
- "What do I have selected in my IDE?"
- "Explain this code" (while having code selected)
- "What errors does WebStorm see?"
- "What files do I have open?"
- "What branch am I on?"
- "What function is this?"

## MCP Tools

| Tool | Description |
|------|-------------|
| `get_ide_selection` | Get selected text with file path, line numbers, language |
| `get_ide_context` | Get cursor position and file info (even without selection) |
| `get_ide_errors` | Get errors and warnings from IDE for current file |
| `get_open_files` | Get list of currently open files/tabs |
| `get_git_status` | Get current branch and changed files |
| `get_recent_files` | Get list of recently opened files |
| `get_symbol_at_cursor` | Get info about function/class at cursor position |

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

### GET http://localhost:63343/api/errors

```json
{
  "filePath": "/path/to/file.ts",
  "problems": [
    {"message": "Cannot find name 'foo'", "severity": "ERROR", "line": 10, "column": 5}
  ]
}
```

### GET http://localhost:63343/api/open-files

```json
{
  "projectName": "my-project",
  "files": [
    {"filePath": "/path/to/file.ts", "fileName": "file.ts", "isActive": true, "isModified": false}
  ]
}
```

### GET http://localhost:63343/api/git-status

```json
{
  "branch": "main",
  "repoRoot": "/path/to/repo",
  "changes": [
    {"filePath": "src/index.ts", "status": "MODIFIED"}
  ]
}
```

### GET http://localhost:63343/api/health

```json
{
  "status": "ok",
  "plugin": "ide-bridge",
  "version": "1.1.0"
}
```

## Automatic Updates

The plugin checks for updates on GitHub releases at every startup. When a new version is available, you'll see a notification with a download link.

If you installed via **Custom Plugin Repository**, updates will appear in WebStorm's plugin update mechanism automatically.

## Requirements

- **WebStorm/IntelliJ:** 2025.1+
- **Java:** 21+ (for building plugin)
- **Node.js:** 20+ (for MCP server)
- **Claude Code:** Latest version with MCP support

## Troubleshooting

### MCP not showing in Claude Code

1. Check your `.mcp.json` path is correct (must be absolute path)
2. Verify the file is valid JSON (no trailing commas)
3. Restart Claude Code completely (`/exit` then relaunch)
4. Check MCP server builds without errors: `cd mcp-server && pnpm build`

### Test plugin is running

```bash
curl http://localhost:63343/api/health
# Should return: {"status":"ok","plugin":"ide-bridge","version":"1.1.0"}
```

If this fails:
- Make sure WebStorm is running
- Check WebStorm logs for errors

### Test selection endpoint

```bash
# Select some code in WebStorm first, then:
curl http://localhost:63343/api/selection
# Returns current selection data or empty object {}
```

### Claude says "No selection" or tool fails

1. Make sure WebStorm is running and has focus
2. Actually select text (not just place cursor)
3. Test with curl first to verify plugin works

### Check WebStorm logs

**Help** → **Show Log in Finder** → search for "IDE Bridge"

### Check MCP server logs

Run MCP server manually to see errors:

```bash
node /path/to/webstorm-mcp/mcp-server/dist/index.js
# Should start without errors
# Ctrl+C to stop
```

## License

MIT

## Author

[@floatrx](https://github.com/floatrx)
