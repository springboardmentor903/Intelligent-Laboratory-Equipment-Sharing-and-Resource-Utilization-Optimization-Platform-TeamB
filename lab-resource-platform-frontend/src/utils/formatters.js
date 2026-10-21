// Formats timestamps into a readable date and time (e.g. "Oct 20, 2026, 3:00 PM").
export const formatDate = (date) => {
  if (!date) return 'N/A';
  try {
    return new Intl.DateTimeFormat('en-US', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(date));
  } catch {
    return String(date);
  }
};

// Truncates text exceeding maxLength and appends an ellipsis.
export const truncateText = (text, maxLength = 80) => {
  if (!text || text.length <= maxLength) return text || '';
  return `${text.slice(0, maxLength)}...`;
};
