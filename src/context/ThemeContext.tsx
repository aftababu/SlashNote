import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useMemo,
  type ReactNode,
} from "react";
import { invoke } from "@tauri-apps/api/core";
import { getSettings, updateSettings } from "../services/notes";
import type {
  ThemeSettings,
  AppThemeName,
  EditorFontSettings,
  FontFamily,
  TextDirection,
  EditorWidth,
  CustomColors,
  ThemeColorKey,
  SoftPaperFolderColorKey,
  SoftPaperFolderColors,
} from "../types/note";

type ThemeMode = "light" | "dark" | "system";

const STORAGE_KEY_THEME_MODE = "slashnote:themeMode";
const STORAGE_KEY_THEME_NAME = "slashnote:themeName";
const STORAGE_KEY_SP_FOLDER_LIGHT = "slashnote:spFolderColorsLight";
const STORAGE_KEY_SP_FOLDER_DARK = "slashnote:spFolderColorsDark";
const STORAGE_KEY_FOLDER_CATEGORIES = "slashnote:folderCategories";

// Font family CSS values
const fontFamilyMap: Record<FontFamily, string> = {
  "system-sans":
    '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  serif: 'ui-serif, Georgia, Cambria, "Times New Roman", Times, serif',
  monospace:
    "ui-monospace, 'SF Mono', SFMono-Regular, Menlo, Monaco, 'Courier New', monospace",
};

// Editor width CSS values for presets
const editorWidthMap: Record<Exclude<EditorWidth, "custom">, string> = {
  narrow: "36rem",
  normal: "48rem",
  wide: "64rem",
  full: "100%",
};

// Default custom width in px
const DEFAULT_CUSTOM_WIDTH_PX = 768;

// Default editor font settings (simplified)
const defaultEditorFontSettings: Required<EditorFontSettings> = {
  baseFontFamily: "system-sans",
  baseFontSize: 15,
  boldWeight: 600,
  lineHeight: 1.6,
};

// Default theme colors (must match App.css :root / .dark values)
const defaultThemeColors: Record<
  "light" | "dark",
  Record<ThemeColorKey, string>
> = {
  light: {
    bg: "#ffffff",
    "bg-secondary": "#f7f7f8",
    "bg-muted": "rgba(24, 24, 27, 0.05)",
    "bg-emphasis": "rgba(24, 24, 27, 0.08)",
    text: "#18181b",
    "text-muted": "#71717a",
    border: "#e5e5e7",
    accent: "#18181b",
    selection: "rgba(250, 204, 21, 0.4)",
  },
  dark: {
    bg: "#1c1c1f",
    "bg-secondary": "#141416",
    "bg-muted": "rgba(255, 255, 255, 0.05)",
    "bg-emphasis": "rgba(255, 255, 255, 0.08)",
    text: "#f4f4f5",
    "text-muted": "#a1a1aa",
    border: "#2e2e33",
    accent: "#f4f4f5",
    selection: "rgba(253, 224, 71, 0.35)",
  },
};

// Obsidian Soft Paper theme colors (Light & Dark)
const softPaperThemeColors: Record<
  "light" | "dark",
  Record<ThemeColorKey, string>
> = {
  light: {
    bg: "#f2ece4",
    "bg-secondary": "#eae2d8",
    "bg-muted": "#dfd6cb",
    "bg-emphasis": "rgba(84, 80, 122, 0.12)",
    text: "#464255",
    "text-muted": "#7a7485",
    border: "#d8cfc3",
    accent: "#54507a",
    selection: "rgba(114, 163, 168, 0.28)",
  },
  dark: {
    bg: "#242730",
    "bg-secondary": "#1e2028",
    "bg-muted": "#1b1d24",
    "bg-emphasis": "rgba(184, 196, 224, 0.12)",
    text: "#cfd4e0",
    "text-muted": "#8b91a1",
    border: "#343844",
    accent: "#5aa8b0",
    selection: "rgba(90, 168, 176, 0.28)",
  },
};

export const defaultSoftPaperFolderColors: Record<
  "light" | "dark",
  Record<SoftPaperFolderColorKey, string>
> = {
  light: {
    "folder-1": "#bf7b87",
    "folder-2": "#9d8ab5",
    "folder-3": "#cf9855",
    "folder-expanded": "#72a3a8",
    "folder-default": "#918e8a",
    "root-note": "#dad7d2",
  },
  dark: {
    "folder-1": "#cf7878",
    "folder-2": "#a888c2",
    "folder-3": "#b8b040",
    "folder-expanded": "#1fa3ac",
    "folder-default": "#8c8f94",
    "root-note": "#5c6274",
  },
};

export { defaultThemeColors, softPaperThemeColors };

// Compute WCAG relative luminance for an RGB triple (0..255)
function relativeLuminance([r, g, b]: [number, number, number]): number {
  const toLinear = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * toLinear(r) + 0.7152 * toLinear(g) + 0.0722 * toLinear(b);
}

function getReadableTextForBg(
  bgColor: string,
  defaultText: string,
  isCustomized: boolean,
): string {
  if (!isCustomized) return defaultText;
  const rgb = parseCssColorToRgb(bgColor);
  if (!rgb) return defaultText;
  const lum = relativeLuminance(rgb);
  return lum > 0.42 ? "#1c1e26" : "#fbf9f6";
}

// Normalize any CSS color string (hex, rgb(), rgba(), hsl(), named) to an RGB
// triple by letting the browser parse it via getComputedStyle.
function parseCssColorToRgb(value: string): [number, number, number] | null {
  if (typeof document === "undefined") return null;
  const probe = document.createElement("div");
  probe.style.color = value;
  probe.style.display = "none";
  document.body.appendChild(probe);
  const computed = getComputedStyle(probe).color;
  document.body.removeChild(probe);
  const match = computed.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
  if (!match) return null;
  return [Number(match[1]), Number(match[2]), Number(match[3])];
}

interface ThemeContextType {
  theme: ThemeMode;
  themeName: AppThemeName;
  resolvedTheme: "light" | "dark";
  setTheme: (theme: ThemeMode) => void;
  setThemeName: (name: AppThemeName) => void;
  cycleTheme: () => void;
  editorFontSettings: Required<EditorFontSettings>;
  setEditorFontSetting: <K extends keyof EditorFontSettings>(
    key: K,
    value: EditorFontSettings[K],
  ) => void;
  resetEditorFontSettings: () => void;
  reloadSettings: () => Promise<void>;
  textDirection: TextDirection;
  setTextDirection: (dir: TextDirection) => void;
  editorWidth: EditorWidth;
  setEditorWidth: (width: EditorWidth) => void;
  interfaceZoom: number;
  setInterfaceZoom: (
    zoomOrUpdater: number | ((prev: number) => number),
  ) => void;
  customEditorWidthPx: number;
  setCustomEditorWidthPx: (px: number) => void;
  setEditorMaxWidthLive: (value: string) => void;
  customColorsLight: CustomColors;
  customColorsDark: CustomColors;
  setCustomColor: (
    mode: "light" | "dark",
    key: ThemeColorKey,
    value: string,
  ) => void;
  resetCustomColor: (mode: "light" | "dark", key: ThemeColorKey) => void;
  resetAllCustomColors: (mode: "light" | "dark") => void;
  softPaperFolderColorsLight: SoftPaperFolderColors;
  softPaperFolderColorsDark: SoftPaperFolderColors;
  setSoftPaperFolderColor: (
    mode: "light" | "dark",
    key: SoftPaperFolderColorKey,
    value: string,
  ) => void;
  resetSoftPaperFolderColor: (
    mode: "light" | "dark",
    key: SoftPaperFolderColorKey,
  ) => void;
  resetAllSoftPaperFolderColors: (mode: "light" | "dark") => void;
  folderCategories: Record<string, number>;
  setFolderCategory: (
    folderPath: string,
    category: number | null,
  ) => Promise<void>;
  migrateFolderCategory: (oldPath: string, newPath: string) => Promise<void>;
}

const ThemeContext = createContext<ThemeContextType | null>(null);

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("useTheme must be used within ThemeProvider");
  }
  return context;
}

interface ThemeProviderProps {
  children: ReactNode;
}

// Apply editor font CSS variables (with computed values)
function applyFontCSSVariables(fonts: Required<EditorFontSettings>) {
  const root = document.documentElement;
  const fontFamily = fontFamilyMap[fonts.baseFontFamily];
  const baseSize = fonts.baseFontSize;
  const boldWeight = fonts.boldWeight;
  const lineHeight = fonts.lineHeight;

  // Base font settings
  root.style.setProperty("--editor-font-family", fontFamily);
  root.style.setProperty("--editor-base-font-size", `${baseSize}px`);
  root.style.setProperty("--editor-bold-weight", String(boldWeight));
  root.style.setProperty("--editor-line-height", String(lineHeight));

  // Computed header sizes (based on base)
  root.style.setProperty("--editor-h1-size", `${baseSize * 2.25}px`);
  root.style.setProperty("--editor-h2-size", `${baseSize * 1.75}px`);
  root.style.setProperty("--editor-h3-size", `${baseSize * 1.5}px`);
  root.style.setProperty("--editor-h4-size", `${baseSize * 1.25}px`);
  root.style.setProperty("--editor-h5-size", `${baseSize}px`);
  root.style.setProperty("--editor-h6-size", `${baseSize}px`);

  // Fixed value for paragraph spacing
  root.style.setProperty("--editor-paragraph-spacing", "0.875em");
}

// Apply editor layout width CSS variables
function applyLayoutCSSVariables(width: EditorWidth, customWidthPx?: number) {
  const root = document.documentElement;
  if (width === "custom" && customWidthPx) {
    root.style.setProperty("--editor-max-width", `${customWidthPx}px`);
  } else if (width !== "custom") {
    root.style.setProperty("--editor-max-width", editorWidthMap[width]);
  }
}

function isTextDirection(value: unknown): value is TextDirection {
  return value === "auto" || value === "ltr" || value === "rtl";
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const [theme, setThemeState] = useState<ThemeMode>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_THEME_MODE);
      if (saved === "light" || saved === "dark" || saved === "system")
        return saved;
    } catch {}
    return "system";
  });
  const [themeName, setThemeNameState] = useState<AppThemeName>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_THEME_NAME);
      if (saved === "slashnote" || saved === "obsidian-soft-paper")
        return saved;
    } catch {}
    return "slashnote";
  });
  const [editorFontSettings, setEditorFontSettings] = useState<
    Required<EditorFontSettings>
  >(defaultEditorFontSettings);
  const [textDirection, setTextDirectionState] =
    useState<TextDirection>("auto");
  const [editorWidth, setEditorWidthState] = useState<EditorWidth>("normal");
  const [interfaceZoom, setInterfaceZoomState] = useState(1.0);
  const [customEditorWidthPx, setCustomEditorWidthPxState] = useState<number>(
    DEFAULT_CUSTOM_WIDTH_PX,
  );
  const [customColorsLight, setCustomColorsLightState] = useState<CustomColors>(
    {},
  );
  const [customColorsDark, setCustomColorsDarkState] = useState<CustomColors>(
    {},
  );
  const [softPaperFolderColorsLight, setSoftPaperFolderColorsLightState] =
    useState<SoftPaperFolderColors>(() => {
      try {
        const raw = localStorage.getItem(STORAGE_KEY_SP_FOLDER_LIGHT);
        if (raw) return JSON.parse(raw);
      } catch {}
      return {};
    });
  const [softPaperFolderColorsDark, setSoftPaperFolderColorsDarkState] =
    useState<SoftPaperFolderColors>(() => {
      try {
        const raw = localStorage.getItem(STORAGE_KEY_SP_FOLDER_DARK);
        if (raw) return JSON.parse(raw);
      } catch {}
      return {};
    });
  const [folderCategories, setFolderCategoriesState] = useState<
    Record<string, number>
  >(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY_FOLDER_CATEGORIES);
      if (raw) return JSON.parse(raw);
    } catch {}
    return {};
  });
  const [isInitialized, setIsInitialized] = useState(false);

  const [systemTheme, setSystemTheme] = useState<"light" | "dark">(() => {
    return window.matchMedia("(prefers-color-scheme: dark)").matches
      ? "dark"
      : "light";
  });

  // Function to load settings from backend
  const loadSettingsFromBackend = useCallback(async () => {
    try {
      const settings = await getSettings();
      if (settings.theme) {
        const mode = settings.theme.mode as ThemeMode;
        if (mode === "light" || mode === "dark" || mode === "system") {
          setThemeState(mode);
          try {
            localStorage.setItem(STORAGE_KEY_THEME_MODE, mode);
          } catch {}
        }
        const name = settings.theme.name as AppThemeName | undefined;
        if (name === "slashnote" || name === "obsidian-soft-paper") {
          setThemeNameState(name);
          try {
            localStorage.setItem(STORAGE_KEY_THEME_NAME, name);
          } catch {}
        }
      }
      if (settings.editorFont) {
        // Filter out null/undefined values to preserve defaults
        const fontSettings = Object.fromEntries(
          Object.entries(settings.editorFont).filter(([, v]) => v != null),
        ) as Partial<EditorFontSettings>;
        setEditorFontSettings({
          ...defaultEditorFontSettings,
          ...fontSettings,
        });
      }
      if (isTextDirection(settings.textDirection)) {
        setTextDirectionState(settings.textDirection);
      }
      if (
        settings.editorWidth === "narrow" ||
        settings.editorWidth === "normal" ||
        settings.editorWidth === "wide" ||
        settings.editorWidth === "full" ||
        settings.editorWidth === "custom"
      ) {
        setEditorWidthState(settings.editorWidth);
      }
      if (
        typeof settings.interfaceZoom === "number" &&
        settings.interfaceZoom >= 0.7 &&
        settings.interfaceZoom <= 1.5
      ) {
        setInterfaceZoomState(settings.interfaceZoom);
      }
      if (
        typeof settings.customEditorWidthPx === "number" &&
        settings.customEditorWidthPx >= 480
      ) {
        setCustomEditorWidthPxState(settings.customEditorWidthPx);
      }
      if (settings.customColorsLight) {
        setCustomColorsLightState(settings.customColorsLight);
      }
      if (settings.customColorsDark) {
        setCustomColorsDarkState(settings.customColorsDark);
      }
      if (settings.softPaperFolderColorsLight) {
        setSoftPaperFolderColorsLightState(settings.softPaperFolderColorsLight);
        try {
          localStorage.setItem(
            STORAGE_KEY_SP_FOLDER_LIGHT,
            JSON.stringify(settings.softPaperFolderColorsLight),
          );
        } catch {}
      }
      if (settings.softPaperFolderColorsDark) {
        setSoftPaperFolderColorsDarkState(settings.softPaperFolderColorsDark);
        try {
          localStorage.setItem(
            STORAGE_KEY_SP_FOLDER_DARK,
            JSON.stringify(settings.softPaperFolderColorsDark),
          );
        } catch {}
      }
      if (settings.folderCategories) {
        setFolderCategoriesState(settings.folderCategories);
        try {
          localStorage.setItem(
            STORAGE_KEY_FOLDER_CATEGORIES,
            JSON.stringify(settings.folderCategories),
          );
        } catch {}
      }
    } catch {
      // If settings can't be loaded, use defaults / localStorage
    }
  }, []);

  // Reload settings from backend (exposed to context consumers)
  const reloadSettings = useCallback(async () => {
    await loadSettingsFromBackend();
  }, [loadSettingsFromBackend]);

  // Load settings from backend on mount
  useEffect(() => {
    loadSettingsFromBackend().finally(() => {
      setIsInitialized(true);
    });
  }, [loadSettingsFromBackend]);

  // Listen for system theme changes
  useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = (e: MediaQueryListEvent) => {
      setSystemTheme(e.matches ? "dark" : "light");
    };
    mediaQuery.addEventListener("change", handler);
    return () => mediaQuery.removeEventListener("change", handler);
  }, []);

  // Resolve the actual theme to use
  const resolvedTheme = theme === "system" ? systemTheme : theme;

  // Apply theme mode and theme name classes to document
  useEffect(() => {
    const root = document.documentElement;
    if (resolvedTheme === "dark") {
      root.classList.add("dark");
    } else {
      root.classList.remove("dark");
    }
    if (themeName === "obsidian-soft-paper") {
      root.classList.add("theme-obsidian-soft-paper");
    } else {
      root.classList.remove("theme-obsidian-soft-paper");
    }
    root.dataset.theme = themeName;
  }, [resolvedTheme, themeName]);

  // Save theme mode and theme name to backend
  const saveThemeSettings = useCallback(
    async (newMode: ThemeMode, newName: AppThemeName) => {
      try {
        localStorage.setItem(STORAGE_KEY_THEME_MODE, newMode);
        localStorage.setItem(STORAGE_KEY_THEME_NAME, newName);
      } catch {}
      try {
        const settings = await getSettings();
        const themeSettings: ThemeSettings = {
          mode: newMode,
          name: newName,
        };
        await updateSettings({
          ...settings,
          theme: themeSettings,
        });
      } catch (error) {
        console.error("Failed to save theme settings:", error);
      }
    },
    [],
  );

  const setTheme = useCallback(
    (newTheme: ThemeMode) => {
      setThemeState(newTheme);
      saveThemeSettings(newTheme, themeName);
    },
    [saveThemeSettings, themeName],
  );

  const setThemeName = useCallback(
    (newName: AppThemeName) => {
      setThemeNameState(newName);
      saveThemeSettings(theme, newName);
    },
    [saveThemeSettings, theme],
  );

  const cycleTheme = useCallback(() => {
    const order: ThemeMode[] = ["light", "dark", "system"];
    const currentIndex = order.indexOf(theme);
    const nextIndex = (currentIndex + 1) % order.length;
    setTheme(order[nextIndex]);
  }, [theme, setTheme]);

  // Apply font CSS variables whenever font settings change
  useEffect(() => {
    applyFontCSSVariables(editorFontSettings);
  }, [editorFontSettings]);

  // Apply layout CSS variables whenever width changes
  useEffect(() => {
    applyLayoutCSSVariables(editorWidth, customEditorWidthPx);
  }, [editorWidth, customEditorWidthPx]);

  // Apply interface zoom whenever it changes (suppress transitions during zoom)
  useEffect(() => {
    const root = document.documentElement;
    root.classList.add("zoom-no-transition");
    root.style.zoom = String(interfaceZoom);
    const raf = requestAnimationFrame(() => {
      root.classList.remove("zoom-no-transition");
    });
    return () => cancelAnimationFrame(raf);
  }, [interfaceZoom]);

  // Save font settings to backend
  const saveFontSettings = useCallback(
    async (newFontSettings: Required<EditorFontSettings>) => {
      try {
        const settings = await getSettings();
        await updateSettings({
          ...settings,
          editorFont: newFontSettings,
        });
      } catch (error) {
        console.error("Failed to save font settings:", error);
      }
    },
    [],
  );

  // Update a single font setting
  const setEditorFontSetting = useCallback(
    <K extends keyof EditorFontSettings>(
      key: K,
      value: EditorFontSettings[K],
    ) => {
      setEditorFontSettings((prev) => {
        const updated = { ...prev, [key]: value };
        saveFontSettings(updated);
        return updated;
      });
    },
    [saveFontSettings],
  );

  // Reset font settings to defaults (single atomic save to avoid race conditions)
  const resetEditorFontSettings = useCallback(async () => {
    setEditorFontSettings(defaultEditorFontSettings);
    setTextDirectionState("auto");
    setEditorWidthState("normal");
    setInterfaceZoomState(1.0);
    setCustomEditorWidthPxState(DEFAULT_CUSTOM_WIDTH_PX);
    setCustomColorsLightState({});
    setCustomColorsDarkState({});
    try {
      const settings = await getSettings();
      await updateSettings({
        ...settings,
        editorFont: defaultEditorFontSettings,
        textDirection: "auto",
        editorWidth: "normal",
        interfaceZoom: 1.0,
        customEditorWidthPx: undefined,
        customColorsLight: undefined,
        customColorsDark: undefined,
      });
    } catch (error) {
      console.error("Failed to reset editor settings:", error);
    }
  }, []);

  // Save and set text direction
  const setTextDirection = useCallback(async (dir: TextDirection) => {
    setTextDirectionState(dir);
    try {
      const settings = await getSettings();
      await updateSettings({ ...settings, textDirection: dir });
    } catch (error) {
      console.error("Failed to save text direction:", error);
    }
  }, []);

  // Save and set editor width
  const setEditorWidth = useCallback(async (width: EditorWidth) => {
    setEditorWidthState(width);
    try {
      const settings = await getSettings();
      await updateSettings({ ...settings, editorWidth: width });
    } catch (error) {
      console.error("Failed to save editor width:", error);
    }
  }, []);

  // Save and set interface zoom (accepts absolute value or updater function)
  const setInterfaceZoom = useCallback(
    (zoomOrUpdater: number | ((prev: number) => number)) => {
      setInterfaceZoomState((prev) => {
        const raw =
          typeof zoomOrUpdater === "function"
            ? zoomOrUpdater(prev)
            : zoomOrUpdater;
        return Math.round(Math.min(Math.max(raw, 0.7), 1.5) * 20) / 20;
      });
    },
    [],
  );

  // Persist interface zoom changes to backend
  useEffect(() => {
    if (!isInitialized) return;
    getSettings()
      .then((settings) => updateSettings({ ...settings, interfaceZoom }))
      .catch((error) => console.error("Failed to save interface zoom:", error));
  }, [interfaceZoom, isInitialized]);

  // Set custom width in px (persists to settings)
  const setCustomEditorWidthPx = useCallback(async (px: number) => {
    setEditorWidthState("custom");
    setCustomEditorWidthPxState(px);
    try {
      const settings = await getSettings();
      await updateSettings({
        ...settings,
        editorWidth: "custom",
        customEditorWidthPx: px,
      });
    } catch (error) {
      console.error("Failed to save custom editor width:", error);
    }
  }, []);

  // Apply custom color CSS variable overrides whenever theme or colors change
  useEffect(() => {
    const root = document.documentElement;
    const activeColors =
      resolvedTheme === "dark" ? customColorsDark : customColorsLight;
    const defaults =
      themeName === "obsidian-soft-paper"
        ? softPaperThemeColors[resolvedTheme]
        : defaultThemeColors[resolvedTheme];
    const keys: ThemeColorKey[] = [
      "bg",
      "bg-secondary",
      "bg-muted",
      "bg-emphasis",
      "text",
      "text-muted",
      "border",
      "accent",
      "selection",
    ];
    for (const key of keys) {
      const value =
        themeName === "obsidian-soft-paper"
          ? defaults[key]
          : (activeColors[key] ?? defaults[key]);
      root.style.setProperty(`--color-${key}`, value);
    }

    if (themeName === "obsidian-soft-paper") {
      const spActive =
        resolvedTheme === "dark"
          ? softPaperFolderColorsDark
          : softPaperFolderColorsLight;
      const spDefaults = defaultSoftPaperFolderColors[resolvedTheme];
      const folderVarMap: Record<
        SoftPaperFolderColorKey,
        { bgVar: string; textVar: string; defaultText: string }
      > = {
        "folder-1": {
          bgVar: "--sp-folder-1",
          textVar: "--sp-folder-1-text",
          defaultText: resolvedTheme === "dark" ? "#26191b" : "#faf5f0",
        },
        "folder-2": {
          bgVar: "--sp-folder-2",
          textVar: "--sp-folder-2-text",
          defaultText: resolvedTheme === "dark" ? "#1e1a28" : "#faf5f0",
        },
        "folder-3": {
          bgVar: "--sp-folder-3",
          textVar: "--sp-folder-3-text",
          defaultText: resolvedTheme === "dark" ? "#24200c" : "#faf5f0",
        },
        "folder-expanded": {
          bgVar: "--sp-folder-expanded",
          textVar: "--sp-folder-expanded-text",
          defaultText: resolvedTheme === "dark" ? "#0d252b" : "#f6fbfb",
        },
        "folder-default": {
          bgVar: "--sp-folder-default",
          textVar: "--sp-folder-default-text",
          defaultText: resolvedTheme === "dark" ? "#1e2024" : "#faf5f0",
        },
        "root-note": {
          bgVar: "--sp-root-note-bg",
          textVar: "--sp-root-note-text",
          defaultText: resolvedTheme === "dark" ? "#e2e4f0" : "#44425a",
        },
      };

      (Object.keys(folderVarMap) as SoftPaperFolderColorKey[]).forEach((k) => {
        const customBg = spActive[k];
        const bg = customBg ?? spDefaults[k];
        const { bgVar, textVar, defaultText } = folderVarMap[k];
        root.style.setProperty(bgVar, bg);
        const textVal = customBg
          ? getReadableTextForBg(customBg, defaultText, true)
          : defaultText;
        root.style.setProperty(textVar, textVal);
      });
    }

    // Sync the Windows title bar to match bg-secondary (no-op on other OSes).
    const captionColor =
      themeName === "obsidian-soft-paper"
        ? defaults["bg-secondary"]
        : (activeColors["bg-secondary"] ?? defaults["bg-secondary"]);
    const rgb = parseCssColorToRgb(captionColor);
    if (rgb) {
      invoke("set_title_bar_theme", {
        isDark: resolvedTheme === "dark",
        r: rgb[0],
        g: rgb[1],
        b: rgb[2],
      }).catch(() => {});
    }
  }, [
    resolvedTheme,
    themeName,
    customColorsLight,
    customColorsDark,
    softPaperFolderColorsLight,
    softPaperFolderColorsDark,
  ]);

  // Set a single custom color for a given mode
  const setCustomColor = useCallback(
    async (mode: "light" | "dark", key: ThemeColorKey, value: string) => {
      const setter =
        mode === "light" ? setCustomColorsLightState : setCustomColorsDarkState;
      const settingsKey =
        mode === "light" ? "customColorsLight" : "customColorsDark";
      setter((prev) => {
        const updated = { ...prev, [key]: value };
        // Persist in background
        getSettings()
          .then((settings) =>
            updateSettings({ ...settings, [settingsKey]: updated }),
          )
          .catch((err) => console.error("Failed to save custom color:", err));
        return updated;
      });
    },
    [],
  );

  // Reset a single custom color back to theme default
  const resetCustomColor = useCallback(
    async (mode: "light" | "dark", key: ThemeColorKey) => {
      const setter =
        mode === "light" ? setCustomColorsLightState : setCustomColorsDarkState;
      const settingsKey =
        mode === "light" ? "customColorsLight" : "customColorsDark";
      setter((prev) => {
        const updated = { ...prev };
        delete updated[key];
        getSettings()
          .then((settings) =>
            updateSettings({
              ...settings,
              [settingsKey]:
                Object.keys(updated).length > 0 ? updated : undefined,
            }),
          )
          .catch((err) => console.error("Failed to reset custom color:", err));
        return updated;
      });
    },
    [],
  );

  // Reset all custom colors for a given mode
  const resetAllCustomColors = useCallback(async (mode: "light" | "dark") => {
    const setter =
      mode === "light" ? setCustomColorsLightState : setCustomColorsDarkState;
    const settingsKey =
      mode === "light" ? "customColorsLight" : "customColorsDark";
    setter({});
    try {
      const settings = await getSettings();
      await updateSettings({ ...settings, [settingsKey]: undefined });
    } catch (err) {
      console.error("Failed to reset all custom colors:", err);
    }
  }, []);

  // Set a single Soft Paper sidebar folder color for a given mode
  const setSoftPaperFolderColor = useCallback(
    async (
      mode: "light" | "dark",
      key: SoftPaperFolderColorKey,
      value: string,
    ) => {
      const setter =
        mode === "light"
          ? setSoftPaperFolderColorsLightState
          : setSoftPaperFolderColorsDarkState;
      const storageKey =
        mode === "light"
          ? STORAGE_KEY_SP_FOLDER_LIGHT
          : STORAGE_KEY_SP_FOLDER_DARK;
      const settingsKey =
        mode === "light"
          ? "softPaperFolderColorsLight"
          : "softPaperFolderColorsDark";
      setter((prev) => {
        const updated = { ...prev, [key]: value };
        try {
          localStorage.setItem(storageKey, JSON.stringify(updated));
        } catch {}
        getSettings()
          .then((settings) =>
            updateSettings({ ...settings, [settingsKey]: updated }),
          )
          .catch((err) =>
            console.error("Failed to save Soft Paper folder color:", err),
          );
        return updated;
      });
    },
    [],
  );

  // Reset a single Soft Paper sidebar folder color back to default
  const resetSoftPaperFolderColor = useCallback(
    async (mode: "light" | "dark", key: SoftPaperFolderColorKey) => {
      const setter =
        mode === "light"
          ? setSoftPaperFolderColorsLightState
          : setSoftPaperFolderColorsDarkState;
      const storageKey =
        mode === "light"
          ? STORAGE_KEY_SP_FOLDER_LIGHT
          : STORAGE_KEY_SP_FOLDER_DARK;
      const settingsKey =
        mode === "light"
          ? "softPaperFolderColorsLight"
          : "softPaperFolderColorsDark";
      setter((prev) => {
        const updated = { ...prev };
        delete updated[key];
        try {
          if (Object.keys(updated).length > 0) {
            localStorage.setItem(storageKey, JSON.stringify(updated));
          } else {
            localStorage.removeItem(storageKey);
          }
        } catch {}
        getSettings()
          .then((settings) =>
            updateSettings({
              ...settings,
              [settingsKey]:
                Object.keys(updated).length > 0 ? updated : undefined,
            }),
          )
          .catch((err) =>
            console.error("Failed to reset Soft Paper folder color:", err),
          );
        return updated;
      });
    },
    [],
  );

  // Reset all Soft Paper sidebar folder colors for a given mode
  const resetAllSoftPaperFolderColors = useCallback(
    async (mode: "light" | "dark") => {
      const setter =
        mode === "light"
          ? setSoftPaperFolderColorsLightState
          : setSoftPaperFolderColorsDarkState;
      const storageKey =
        mode === "light"
          ? STORAGE_KEY_SP_FOLDER_LIGHT
          : STORAGE_KEY_SP_FOLDER_DARK;
      const settingsKey =
        mode === "light"
          ? "softPaperFolderColorsLight"
          : "softPaperFolderColorsDark";
      setter({});
      try {
        localStorage.removeItem(storageKey);
      } catch {}
      try {
        const settings = await getSettings();
        await updateSettings({ ...settings, [settingsKey]: undefined });
      } catch (err) {
        console.error("Failed to reset all Soft Paper folder colors:", err);
      }
    },
    [],
  );

  // Set or remove a folder's category assignment (1, 2, 3 or null to remove)
  const setFolderCategory = useCallback(
    async (folderPath: string, category: number | null) => {
      setFolderCategoriesState((prev) => {
        const updated = { ...prev };
        if (category && category >= 1 && category <= 3) {
          updated[folderPath] = category;
        } else {
          delete updated[folderPath];
        }
        try {
          localStorage.setItem(
            STORAGE_KEY_FOLDER_CATEGORIES,
            JSON.stringify(updated),
          );
        } catch {}
        getSettings()
          .then((settings) =>
            updateSettings({
              ...settings,
              folderCategories:
                Object.keys(updated).length > 0 ? updated : undefined,
            }),
          )
          .catch((err) =>
            console.error("Failed to save folder category:", err),
          );
        return updated;
      });
    },
    [],
  );

  // Migrate folder category key on folder rename
  const migrateFolderCategory = useCallback(
    async (oldPath: string, newPath: string) => {
      setFolderCategoriesState((prev) => {
        if (!prev[oldPath]) return prev;
        const updated = { ...prev };
        updated[newPath] = updated[oldPath];
        delete updated[oldPath];
        try {
          localStorage.setItem(
            STORAGE_KEY_FOLDER_CATEGORIES,
            JSON.stringify(updated),
          );
        } catch {}
        getSettings()
          .then((settings) =>
            updateSettings({
              ...settings,
              folderCategories: updated,
            }),
          )
          .catch((err) =>
            console.error("Failed to migrate folder category on rename:", err),
          );
        return updated;
      });
    },
    [],
  );

  // Live CSS variable update during drag (no persistence)
  const setEditorMaxWidthLive = useCallback((value: string) => {
    document.documentElement.style.setProperty("--editor-max-width", value);
  }, []);

  // Memoize the context value so consumers don't re-render when the provider
  // re-renders for unrelated reasons (only when an actual dependency changes).
  // NOTE: must be called before any early return to satisfy the Rules of Hooks.
  const value = useMemo(
    () => ({
      theme,
      themeName,
      resolvedTheme,
      setTheme,
      setThemeName,
      cycleTheme,
      editorFontSettings,
      setEditorFontSetting,
      resetEditorFontSettings,
      reloadSettings,
      textDirection,
      setTextDirection,
      editorWidth,
      setEditorWidth,
      interfaceZoom,
      setInterfaceZoom,
      customEditorWidthPx,
      setCustomEditorWidthPx,
      setEditorMaxWidthLive,
      customColorsLight,
      customColorsDark,
      setCustomColor,
      resetCustomColor,
      resetAllCustomColors,
      softPaperFolderColorsLight,
      softPaperFolderColorsDark,
      setSoftPaperFolderColor,
      resetSoftPaperFolderColor,
      resetAllSoftPaperFolderColors,
      folderCategories,
      setFolderCategory,
      migrateFolderCategory,
    }),
    [
      theme,
      themeName,
      resolvedTheme,
      setTheme,
      setThemeName,
      cycleTheme,
      editorFontSettings,
      setEditorFontSetting,
      resetEditorFontSettings,
      reloadSettings,
      textDirection,
      setTextDirection,
      editorWidth,
      setEditorWidth,
      interfaceZoom,
      setInterfaceZoom,
      customEditorWidthPx,
      setCustomEditorWidthPx,
      setEditorMaxWidthLive,
      customColorsLight,
      customColorsDark,
      setCustomColor,
      resetCustomColor,
      resetAllCustomColors,
      softPaperFolderColorsLight,
      softPaperFolderColorsDark,
      setSoftPaperFolderColor,
      resetSoftPaperFolderColor,
      resetAllSoftPaperFolderColors,
      folderCategories,
      setFolderCategory,
      migrateFolderCategory,
    ],
  );

  // Don't render until initialized to prevent flash
  if (!isInitialized) {
    return null;
  }

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}
