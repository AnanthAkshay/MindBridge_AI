import { useThemeStore } from "../store/theme-store";

export function useTheme() {
  return useThemeStore();
}
