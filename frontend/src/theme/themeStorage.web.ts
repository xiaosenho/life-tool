export const themeStorage = {
  getItem(key: string) {
    return typeof globalThis.localStorage === "undefined" ? null : globalThis.localStorage.getItem(key);
  },
  setItem(key: string, value: string) {
    globalThis.localStorage?.setItem(key, value);
  }
};
