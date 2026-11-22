"use client";

import {
  TeamLanyardRail,
  type LanyardMember,
} from "@/components/ui/team-lanyard-rail";

const MEMBERS: LanyardMember[] = [
  {
    id: "m-1",
    name: "Elena Rostova",
    role: "Staff Engineer",
    team: "Core · Platform",
    image:
      "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=320&auto=format&fit=crop&q=80",
    bio: "Building distributed primitives and making sure our cache never goes cold. Coffee nerd.",
    tags: ["Rust", "Distributed", "Infra"],
    links: [
      { label: "GitHub", href: "https://github.com" },
      { label: "X", href: "https://x.com" },
    ],
    tone: 1,
  },
  {
    id: "m-2",
    name: "Marcus Vance",
    role: "Product Designer",
    team: "Design · Design Systems",
    image:
      "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=320&auto=format&fit=crop&q=80",
    bio: "Obsessed with micro-interactions, type ramps, and accessible contrast ratios. Always tweaking.",
    tags: ["Figma", "Design systems", "a11y"],
    links: [
      { label: "Read.cv", href: "https://read.cv" },
      { label: "Dribbble", href: "https://dribbble.com" },
    ],
    tone: 2,
    drop: 12,
  },
  {
    id: "m-3",
    name: "Amina Diallo",
    role: "Founding Engineer",
    team: "Studio · Growth",
    image:
      "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=320&auto=format&fit=crop&q=80",
    bio: "Turning messy telemetry into fast feedback loops. Previously founded a devtools startup.",
    tags: ["TypeScript", "Next.js", "Analytics"],
    links: [{ label: "Substack", href: "https://substack.com" }],
    tone: 3,
  },
  {
    id: "m-4",
    name: "Taro Takahashi",
    role: "Principal Architect",
    team: "Core · Architecture",
    image:
      "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=320&auto=format&fit=crop&q=80",
    bio: "Writing RFCs nobody asked for and untangling dependency graphs since 2012.",
    tags: ["Compilers", "Go", "RFCs"],
    links: [
      { label: "Blog", href: "https://example.com" },
      { label: "GitHub", href: "https://github.com" },
    ],
    tone: 4,
    drop: 8,
  },
  {
    id: "m-5",
    name: "Chloe Martin",
    role: "Head of Community",
    team: "Marketing · DevRel",
    image:
      "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=320&auto=format&fit=crop&q=80",
    bio: "Hosting meetups, editing our changelog, and running the open-source discord.",
    tags: ["Community", "Content", "DevRel"],
    links: [
      { label: "Discord", href: "https://discord.com" },
      { label: "YouTube", href: "https://youtube.com" },
    ],
    tone: 5,
  },
];

export default function TeamLanyardRailDemo() {
  return (
    <div className="flex w-full items-center justify-center p-6 md:p-10">
      <div className="w-full max-w-4xl">
        <TeamLanyardRail
          items={MEMBERS}
          eyebrow="The people behind it"
          heading="Meet the team"
          caption="Hover or swipe through badges. Click to flip and inspect links."
        />
      </div>
    </div>
  );
}
