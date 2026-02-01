#!/usr/bin/env node
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';

const WEBSTORM_PORT = 63343;
const WEBSTORM_URL = `http://localhost:${WEBSTORM_PORT}/api/selection`;

interface IdeSelection {
  text: string;
  filePath: string;
  startLine: number;
  endLine: number;
  startColumn: number;
  endColumn: number;
  language: string;
  projectName: string;
}

interface IdeResponse {
  success: boolean;
  data?: IdeSelection;
  error?: string;
}

async function fetchIdeSelection(): Promise<IdeResponse> {
  try {
    const response = await fetch(WEBSTORM_URL, {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
    });

    if (!response.ok) {
      return { success: false, error: `HTTP ${response.status}: ${response.statusText}` };
    }

    const data = await response.json() as IdeSelection;
    return { success: true, data };
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error';
    return { success: false, error: `Failed to connect to WebStorm: ${message}` };
  }
}

function formatSelection(selection: IdeSelection): string {
  const lines = selection.startLine === selection.endLine
    ? `line ${selection.startLine}`
    : `lines ${selection.startLine}-${selection.endLine}`;

  return [
    `**File:** \`${selection.filePath}\` (${lines})`,
    `**Language:** ${selection.language}`,
    `**Project:** ${selection.projectName}`,
    '',
    '```' + selection.language.toLowerCase(),
    selection.text,
    '```',
  ].join('\n');
}

// Create MCP server
const server = new McpServer({
  name: 'webstorm-bridge',
  version: '1.0.0',
});

// Register tool: get_ide_selection
server.tool(
  'get_ide_selection',
  'Get the currently selected text from WebStorm IDE, including file path, line numbers, and language',
  {},
  async () => {
    const result = await fetchIdeSelection();

    if (!result.success || !result.data) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `Unable to get IDE selection: ${result.error}\n\nMake sure:\n1. WebStorm is running\n2. The IDE Bridge plugin is installed and enabled\n3. You have text selected in the editor`,
          },
        ],
      };
    }

    const { data } = result;

    // Handle empty selection
    if (!data.text || data.text.trim() === '') {
      return {
        content: [
          {
            type: 'text' as const,
            text: `No text selected in WebStorm.\n\n**Current file:** \`${data.filePath}\`\n**Cursor at:** line ${data.startLine}, column ${data.startColumn}`,
          },
        ],
      };
    }

    return {
      content: [
        {
          type: 'text' as const,
          text: formatSelection(data),
        },
      ],
    };
  }
);

// Register tool: get_ide_context
server.tool(
  'get_ide_context',
  'Get current cursor position and file context from WebStorm IDE (even without selection)',
  {},
  async () => {
    const result = await fetchIdeSelection();

    if (!result.success || !result.data) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `Unable to get IDE context: ${result.error}`,
          },
        ],
      };
    }

    const { data } = result;

    return {
      content: [
        {
          type: 'text' as const,
          text: [
            `**File:** \`${data.filePath}\``,
            `**Project:** ${data.projectName}`,
            `**Language:** ${data.language}`,
            `**Position:** line ${data.startLine}, column ${data.startColumn}`,
            data.text ? `**Selected:** ${data.text.length} characters` : '**Selected:** none',
          ].join('\n'),
        },
      ],
    };
  }
);

// Start server
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error('WebStorm Bridge MCP server running');
}

main().catch(console.error);
