import "@testing-library/jest-dom/vitest";

function createStorage() {
  const storage = new Map<string, string>();

  return {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => {
      storage.set(key, value);
    },
    removeItem: (key: string) => {
      storage.delete(key);
    },
    clear: () => {
      storage.clear();
    }
  };
}

Object.defineProperty(window, "localStorage", {
  configurable: true,
  value: createStorage()
});

Object.defineProperty(window, "sessionStorage", {
  configurable: true,
  value: createStorage()
});
