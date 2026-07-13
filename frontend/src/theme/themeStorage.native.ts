import Storage from "expo-sqlite/kv-store";

export const themeStorage = {
  getItem(key: string) {
    return Storage.getItemSync(key);
  },
  setItem(key: string, value: string) {
    Storage.setItemSync(key, value);
  }
};
