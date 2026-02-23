import os
from pathlib import Path

BASE_DIR = Path("/home/tstapler/dotfiles/stapler-scripts/llm-sync")
SRC_DIR = BASE_DIR / "src"
SOURCES_DIR = SRC_DIR / "sources"
TARGETS_DIR = SRC_DIR / "targets"

# Ensure directories exist
SOURCES_DIR.mkdir(parents=True, exist_ok=True)
TARGETS_DIR.mkdir(parents=True, exist_ok=True)

# 1. src/core.py
core_content = """from dataclasses import dataclass, field
from typing import List, Dict, Any, Optional
from abc import ABC, abstractmethod

@dataclass
class Skill:
    """Universal representation of an LLM skill/agent."""
    name: str
    description: str
    content: str  # The system prompt / instructions
    tools: Dict[str, bool] = field(default_factory=dict)
    metadata: Dict[str, Any] = field(default_factory=dict)
    
    # Track origin for logging
    source_file: Optional[str] = None

class SkillSource(ABC):
    @abstractmethod
    def load(self) -> List[Skill]:
        """Load skills from the source."""
        pass

class SkillTarget(ABC):
    @abstractmethod
    def save(self, skills: List[Skill], dry_run: bool = False, force: bool = False) -> int:
        """Save skills to the target. Returns number of skills saved."""
        pass
"""

with open(SRC_DIR / "core.py", "w") as f:
    f.write(core_content)

# 2. src/sources/claude.py
claude_content = """import yaml
from pathlib import Path
from typing import List, Dict, Any, Optional
from ..core import Skill, SkillSource
from rich.console import Console

console = Console()

class ClaudeSource(SkillSource):
    def __init__(self, agents_dir: Optional[Path] = None, commands_dir: Optional[Path] = None):
        self.agents_dir = agents_dir or Path.home() / ".claude" / "agents"
        # self.commands_dir = commands_dir or Path.home() / "Documents" / "personal-wiki" / ".claude" / "commands"

    def load(self) -> List[Skill]:
        skills = []
        if self.agents_dir.exists():
            for agent_file in self.agents_dir.glob("**/*.md"):
                skill = self._load_agent(agent_file)
                if skill:
                    skills.append(skill)
        return skills

    def _load_agent(self, agent_file: Path) -> Optional[Skill]:
        try:
            with open(agent_file, 'r', encoding='utf-8') as f:
                content = f.read()

            if content.startswith('---'):
                parts = content.split('---', 2)
                if len(parts) >= 3:
                    frontmatter = parts[1].strip()
                    agent_content = parts[2].strip()
                    
                    try:
                        metadata = yaml.safe_load(frontmatter)
                    except yaml.YAMLError:
                        metadata = self._parse_frontmatter_manually(frontmatter)
                    
                    if not metadata:
                        return None
                        
                    name = metadata.get('name') or agent_file.stem
                    description = metadata.get('description', '')
                    
                    # Convert tools
                    claude_tools = metadata.get('tools', [])
                    tools = self._convert_tools(claude_tools)
                    
                    return Skill(
                        name=name,
                        description=description,
                        content=agent_content,
                        tools=tools,
                        metadata=metadata,
                        source_file=str(agent_file)
                    )
        except Exception as e:
            console.print(f"[red]Error reading {agent_file}: {e}[/red]")
        return None

    def _parse_frontmatter_manually(self, frontmatter: str) -> Optional[Dict[str, Any]]:
        # Simple manual parser for when YAML fails (often due to unquoted strings or 'tools: *')
        metadata = {}
        for line in frontmatter.split('\n'):
            if ':' in line:
                key, value = line.split(':', 1)
                key = key.strip()
                value = value.strip()
                if value:
                    metadata[key] = value
        return metadata

    def _convert_tools(self, claude_tools: Any) -> Dict[str, bool]:
        # Basic mapping - can be expanded
        # Gemini uses 'google_web_search', 'run_shell_command', etc.
        # Claude uses 'bash', 'read_file', etc.
        
        # This mapping needs to be smart. For now, we'll map common ones.
        tool_map = {
            'bash': 'run_shell_command',
            'read': 'read_file',
            'write': 'write_file',
            'glob': 'glob',
            'grep': 'search_file_content',
            'webfetch': 'web_fetch'
        }
        
        result = {}
        
        # Helper to process a single tool string
        def process_tool(t_name):
            t_name = t_name.lower().strip()
            if t_name in ['*', 'all']:
                # Enable all equivalent Gemini tools? Or just leave empty to imply 'all available'?
                # For now, let's enable common ones
                for v in tool_map.values():
                    result[v] = True
            elif t_name in tool_map:
                result[tool_map[t_name]] = True
        
        if isinstance(claude_tools, str):
            if ',' in claude_tools:
                for t in claude_tools.split(','):
                    process_tool(t)
            else:
                process_tool(claude_tools)
        elif isinstance(claude_tools, list):
            for t in claude_tools:
                process_tool(str(t))
                
        return result
"""

with open(SOURCES_DIR / "claude.py", "w") as f:
    f.write(claude_content)


# 3. src/targets/gemini.py
gemini_content = """import yaml
from pathlib import Path
from typing import List
from ..core import Skill, SkillTarget
from rich.console import Console

console = Console()

class GeminiTarget(SkillTarget):
    def __init__(self, skills_dir: Optional[Path] = None):
        self.skills_dir = skills_dir or Path.home() / ".gemini" / "skills"

    def save(self, skills: List[Skill], dry_run: bool = False, force: bool = False) -> int:
        self.skills_dir.mkdir(parents=True, exist_ok=True)
        saved_count = 0
        
        for skill in skills:
            # Gemini skills are directories with a SKILL.md file
            skill_dir = self.skills_dir / skill.name
            skill_file = skill_dir / "SKILL.md"
            
            if skill_file.exists() and not force:
                console.print(f"[yellow]Skipping {skill.name} (exists). Use --force to overwrite.[/yellow]")
                continue
                
            # Construct SKILL.md content
            # It needs frontmatter similar to the examples
            
            frontmatter = {
                'name': skill.name,
                'description': skill.description,
            }
            
            # Gemini might not strictly enforce tool permissions in SKILL.md yet, 
            # but we can add them to metadata or description if needed.
            # For now, we just pass the main metadata.
            
            fm_yaml = yaml.dump(frontmatter, sort_keys=False)
            
            content = f"---\n{fm_yaml}---\n\n{skill.content}"
            
            if dry_run:
                console.print(f"[blue]Would write {skill_file}[/blue]")
                # console.print(content[:200] + "...")
            else:
                skill_dir.mkdir(exist_ok=True)
                with open(skill_file, 'w', encoding='utf-8') as f:
                    f.write(content)
                console.print(f"[green]Saved {skill.name}[/green]")
                saved_count += 1
                
        return saved_count
"""

with open(TARGETS_DIR / "gemini.py", "w") as f:
    f.write(gemini_content)

# 4. src/main.py
main_content = """import argparse
from pathlib import Path
from sources.claude import ClaudeSource
from targets.gemini import GeminiTarget
from rich.console import Console

console = Console()

def main():
    parser = argparse.ArgumentParser(description="Sync LLM skills from Claude to Gemini")
    parser.add_argument("--dry-run", action="store_true", help="Preview changes")
    parser.add_argument("--force", action="store_true", help="Overwrite existing skills")
    
    args = parser.parse_args()
    
    console.print("[bold]Starting LLM Skill Sync[/bold]")
    
    # 1. Load from Claude
    source = ClaudeSource()
    skills = source.load()
    console.print(f"Found {len(skills)} skills in Claude configuration")
    
    # 2. Save to Gemini
    target = GeminiTarget()
    saved = target.save(skills, dry_run=args.dry_run, force=args.force)
    
    console.print(f"[bold green]Sync Complete. Saved {saved} skills.[/bold green]")

if __name__ == "__main__":
    main()
"""

with open(SRC_DIR / "main.py", "w") as f:
    f.write(main_content)

print("Files created successfully.")
