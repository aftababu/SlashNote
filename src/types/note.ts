export interface NoteMetadata {
  id: string;
  title: string;
  preview: string;
  modified: number;
}

export interface Note {
  id: string;
  title: string;
  content: string;
  path: string;
  modified: number;
}

export type AppThemeName = "slashnote" | "obsidian-soft-paper";

export interface ThemeSettings {
  mode: "light" | "dark" | "system";
  name?: AppThemeName;
}

export type FontFamily = "system-sans" | "serif" | "monospace";
export type TextDirection = "auto" | "ltr" | "rtl";
export type EditorWidth = "narrow" | "normal" | "wide" | "full" | "custom";

export interface EditorFontSettings {
  baseFontFamily?: FontFamily;
  baseFontSize?: number; // in px, default 16
  boldWeight?: number; // 600, 700, 800 for headings and bold text
  lineHeight?: number; // default 1.6
}

// Customizable theme color keys (maps to CSS --color-* variables)
export type ThemeColorKey =
  | "bg"
  | "bg-secondary"
  | "bg-muted"
  | "bg-emphasis"
  | "text"
  | "text-muted"
  | "border"
  | "accent"
  | "selection";

// Partial map of color overrides (hex strings)
export type CustomColors = Partial<Record<ThemeColorKey, string>>;

// Customizable sidebar folder color keys for Obsidian Soft Paper theme
export type SoftPaperFolderColorKey =
  | "folder-1"
  | "folder-2"
  | "folder-3"
  | "folder-expanded"
  | "folder-default"
  | "root-note";

export type SoftPaperFolderColors = Partial<
  Record<SoftPaperFolderColorKey, string>
>;

// Per-folder settings (stored in .slashnote/settings.json)
export interface Settings {
  theme: ThemeSettings;
  editorFont?: EditorFontSettings;
  gitEnabled?: boolean;
  foldersEnabled?: boolean;
  pinnedNoteIds?: string[];
  textDirection?: TextDirection;
  editorWidth?: EditorWidth;
  customEditorWidthPx?: number;
  defaultNoteName?: string;
  interfaceZoom?: number;
  ollamaModel?: string;
  ignoredPatterns?: string[];
  ignoredFileTypes?: string[];
  customColorsLight?: CustomColors;
  customColorsDark?: CustomColors;
  softPaperFolderColorsLight?: SoftPaperFolderColors;
  softPaperFolderColorsDark?: SoftPaperFolderColors;
  folderCategories?: Record<string, number>;
}

export interface FolderNode {
  name: string;
  path: string;
  children: FolderNode[];
  notes: NoteMetadata[];
}
