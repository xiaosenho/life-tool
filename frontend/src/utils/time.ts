const SHANGHAI_TIME_ZONE = "Asia/Shanghai";

function formatParts(date: Date) {
  const formatter = new Intl.DateTimeFormat("zh-CN", {
    timeZone: SHANGHAI_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });

  const parts = formatter.formatToParts(date);
  const map = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return {
    year: map.year,
    month: map.month,
    day: map.day,
    hour: map.hour,
    minute: map.minute,
    second: map.second,
  };
}

export function formatDateTimeCn(value: string | Date) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return typeof value === "string" ? value : "";
  }
  const parts = formatParts(date);
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

export function formatDateCn(value: string | Date) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return typeof value === "string" ? value : "";
  }
  const parts = formatParts(date);
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function todayInShanghai() {
  return formatDateCn(new Date());
}

export function currentMonthInShanghai() {
  const today = todayInShanghai();
  return today.slice(0, 7);
}

