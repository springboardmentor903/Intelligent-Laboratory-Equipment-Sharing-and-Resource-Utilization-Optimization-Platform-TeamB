/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useMemo } from "react";

const SearchContext = createContext({
  searchTerm: "",
  setSearchTerm: () => {},
  searchPlaceholder: "Search...",
  setSearchPlaceholder: () => {},
  showSearch: true,
  setShowSearch: () => {},
});

export function SearchProvider({ children }) {
  const [searchTerm, setSearchTerm] = useState("");
  const [searchPlaceholder, setSearchPlaceholder] = useState("Search equipment, bookings, or records...");
  const [showSearch, setShowSearch] = useState(true);

  const value = useMemo(
    () => ({
      searchTerm,
      setSearchTerm,
      searchPlaceholder,
      setSearchPlaceholder,
      showSearch,
      setShowSearch,
    }),
    [searchTerm, searchPlaceholder, showSearch]
  );

  return (
    <SearchContext.Provider value={value}>
      {children}
    </SearchContext.Provider>
  );
}

export function useSearch() {
  const context = useContext(SearchContext);
  if (!context) {
    throw new Error("useSearch must be used within a SearchProvider");
  }
  return context;
}

