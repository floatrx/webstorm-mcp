#!/usr/bin/env node
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';

const WEBSTORM_PORT = 63343;
const WEBSTORM_BASE = `http://localhost:${WEBSTORM_PORT}`;

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

interface IdeResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

interface IdeProblem {
  message: string;
  severity: 'ERROR' | 'WARNING' | 'INFO';
  line: number;
  column: number;
  description: string;
}

interface IdeProblems {
  filePath: string;
  problems: IdeProblem[];
}

interface IdeOpenFile {
  filePath: string;
  fileName: string;
  isActive: boolean;
  isModified: boolean;
}

interface IdeOpenFiles {
  projectName: string;
  files: IdeOpenFile[];
}

interface IdeGitChange {
  filePath: string;
  status: 'MODIFIED' | 'ADDED' | 'DELETED' | 'MOVED' | 'UNTRACKED';
}

interface IdeGitStatus {
  branch: string;
  repoRoot: string;
  changes: IdeGitChange[];
}

interface IdeRecentFile {
  filePath: string;
  fileName: string;
}

interface IdeRecentFiles {
  projectName: string;
  files: IdeRecentFile[];
}

interface IdeSymbol {
  name: string;
  kind: string;
  filePath: string;
  line: number;
  text: string;
}

async function fetchFromIde<T>(endpoint: string): Promise<IdeResponse<T>> {
  try {
    const response = await fetch(`${WEBSTORM_BASE}${endpoint}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });

    if (!response.ok) {
      return { success: false, error: `HTTP ${response.status}: ${response.statusText}` };
    }

    const data = (await response.json()) as T;
    return { success: true, data };
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error';
    return { success: false, error: `Failed to connect to WebStorm: ${message}` };
  }
}

const fetchIdeSelection = () => fetchFromIde<IdeSelection>('/api/selection');
const fetchIdeProblems = () => fetchFromIde<IdeProblems>('/api/errors');
const fetchIdeOpenFiles = () => fetchFromIde<IdeOpenFiles>('/api/open-files');
const fetchIdeGitStatus = () => fetchFromIde<IdeGitStatus>('/api/git-status');
const fetchIdeRecentFiles = () => fetchFromIde<IdeRecentFiles>('/api/recent-files');
const fetchIdeSymbol = () => fetchFromIde<IdeSymbol>('/api/symbol');

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
  version: '1.1.0',
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

// Register tool: get_ide_errors
server.tool(
  'get_ide_errors',
  'Get errors and warnings from WebStorm IDE for the current file',
  {},
  async () => {
    const result = await fetchIdeProblems();

    if (!result.success || !result.data) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `Unable to get IDE errors: ${result.error}`,
          },
        ],
      };
    }

    const { data } = result;

    if (data.problems.length === 0) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `**File:** \`${data.filePath}\`\n\n✅ No errors or warnings`,
          },
        ],
      };
    }

    const errors = data.problems.filter((p) => p.severity === 'ERROR');
    const warnings = data.problems.filter((p) => p.severity === 'WARNING');
    const infos = data.problems.filter((p) => p.severity === 'INFO');

    const formatProblem = (p: IdeProblem) => `- Line ${p.line}: ${p.message}`;

    const sections: string[] = [`**File:** \`${data.filePath}\``];

    if (errors.length > 0) {
      sections.push(`\n**❌ Errors (${errors.length}):**\n${errors.map(formatProblem).join('\n')}`);
    }
    if (warnings.length > 0) {
      sections.push(`\n**⚠️ Warnings (${warnings.length}):**\n${warnings.map(formatProblem).join('\n')}`);
    }
    if (infos.length > 0) {
      sections.push(`\n**ℹ️ Info (${infos.length}):**\n${infos.map(formatProblem).join('\n')}`);
    }

    return {
      content: [
        {
          type: 'text' as const,
          text: sections.join('\n'),
        },
      ],
    };
  }
);

// Register tool: get_open_files
server.tool(
  'get_open_files',
  'Get list of currently open files/tabs in WebStorm IDE',
  {},
  async () => {
    const result = await fetchIdeOpenFiles();

    if (!result.success || !result.data) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `Unable to get open files: ${result.error}`,
          },
        ],
      };
    }

    const { data } = result;

    if (data.files.length === 0) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `**Project:** ${data.projectName}\n\nNo files open`,
          },
        ],
      };
    }

    const formatFile = (f: IdeOpenFile) => {
      const markers = [f.isActive ? '→' : ' ', f.isModified ? '●' : ' '].join('');
      return `${markers} \`${f.filePath}\``;
    };

    return {
      content: [
        {
          type: 'text' as const,
          text: [
            `**Project:** ${data.projectName}`,
            `**Open files:** ${data.files.length}`,
            '',
            '| Status | File |',
            '|--------|------|',
            ...data.files.map((f) => {
              const status = [f.isActive ? '→ active' : '', f.isModified ? '● modified' : '']
                .filter(Boolean)
                .join(', ') || '—';
              return `| ${status} | \`${f.fileName}\` |`;
            }),
          ].join('\n'),
        },
      ],
    };
  }
);

// Register tool: get_git_status
server.tool(
  'get_git_status',
  'Get Git status from WebStorm IDE - current branch and changed files',
  {},
  async () => {
    const result = await fetchIdeGitStatus();

    if (!result.success || !result.data) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `Unable to get git status: ${result.error}`,
          },
        ],
      };
    }

    const { data } = result;

    if (!data.branch) {
      return {
        content: [
          {
            type: 'text' as const,
            text: 'No Git repository found in current project',
          },
        ],
      };
    }

    const statusIcons: Record<string, string> = {
      MODIFIED: '📝',
      ADDED: '✨',
      DELETED: '🗑️',
      MOVED: '📦',
      UNTRACKED: '❓',
    };

    const sections = [
      `**Branch:** \`${data.branch}\``,
      `**Repo:** \`${data.repoRoot}\``,
    ];

    if (data.changes.length === 0) {
      sections.push('\n✅ Working tree clean');
    } else {
      sections.push(`\n**Changes (${data.changes.length}):**`);
      data.changes.forEach((c) => {
        const icon = statusIcons[c.status] || '•';
        sections.push(`${icon} ${c.status.toLowerCase()}: \`${c.filePath}\``);
      });
    }

    return {
      content: [
        {
          type: 'text' as const,
          text: sections.join('\n'),
        },
      ],
    };
  }
);

// Register tool: get_recent_files
server.tool(
  'get_recent_files',
  'Get list of recently opened files in WebStorm IDE',
  {},
  async () => {
    const result = await fetchIdeRecentFiles();

    if (!result.success || !result.data) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `Unable to get recent files: ${result.error}`,
          },
        ],
      };
    }

    const { data } = result;

    if (data.files.length === 0) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `**Project:** ${data.projectName}\n\nNo recent files`,
          },
        ],
      };
    }

    return {
      content: [
        {
          type: 'text' as const,
          text: [
            `**Project:** ${data.projectName}`,
            `**Recent files (${data.files.length}):**`,
            '',
            ...data.files.map((f, i) => `${i + 1}. \`${f.fileName}\``),
          ].join('\n'),
        },
      ],
    };
  }
);

// Register tool: get_symbol_at_cursor
server.tool(
  'get_symbol_at_cursor',
  'Get information about the symbol (function, class, variable) at the current cursor position in WebStorm IDE',
  {},
  async () => {
    const result = await fetchIdeSymbol();

    if (!result.success || !result.data) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `Unable to get symbol: ${result.error}`,
          },
        ],
      };
    }

    const { data } = result;

    if (!data.name) {
      return {
        content: [
          {
            type: 'text' as const,
            text: 'No symbol found at cursor position',
          },
        ],
      };
    }

    return {
      content: [
        {
          type: 'text' as const,
          text: [
            `**Symbol:** \`${data.name}\``,
            `**Kind:** ${data.kind}`,
            `**File:** \`${data.filePath}\` (line ${data.line})`,
            '',
            '```',
            data.text,
            '```',
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
