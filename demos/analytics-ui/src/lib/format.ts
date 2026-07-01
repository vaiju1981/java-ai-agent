export function formatValue(value: unknown) {
  if (typeof value === "number") {
    return value.toLocaleString(undefined, { maximumFractionDigits: 3 });
  }
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  return String(value);
}

export function fileName(path: string | undefined) {
  if (!path) {
    return "No dataset loaded";
  }
  return path.split("/").at(-1) ?? path;
}
