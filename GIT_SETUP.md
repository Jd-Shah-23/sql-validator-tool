# Git Setup Guide

This guide will help you initialize the repository and push it to GitHub.

## Prerequisites

- Git installed on your system
- GitHub account
- GitHub repository created (or ready to create)

## Step 1: Initialize Git Repository

```bash
cd sql-validator-tool
git init
```

## Step 2: Add All Files

```bash
git add .
```

## Step 3: Create Initial Commit

```bash
git commit -m "Initial commit: Multi-Database SQL Validator Tool

Features:
- Syntax error detection with JSqlParser
- Multi-database validation (DB2, PostgreSQL, Oracle)
- Database-agnostic SQL rewrites
- Multi-issue query handling
- Comprehensive reporting

Version: 3.0.0"
```

## Step 4: Create GitHub Repository

1. Go to https://github.com/new
2. Repository name: `sql-validator-tool` (or your preferred name)
3. Description: "Multi-Database SQL Validator for DB2, PostgreSQL, and Oracle"
4. Choose Public or Private
5. **DO NOT** initialize with README, .gitignore, or license (we already have these)
6. Click "Create repository"

## Step 5: Add Remote and Push

Replace `YOUR_USERNAME` with your GitHub username:

```bash
# Add remote repository
git remote add origin https://github.com/YOUR_USERNAME/sql-validator-tool.git

# Verify remote
git remote -v

# Push to GitHub
git branch -M main
git push -u origin main
```

## Step 6: Verify

Visit your repository on GitHub to confirm all files are uploaded:
```
https://github.com/YOUR_USERNAME/sql-validator-tool
```

## Alternative: Using SSH

If you prefer SSH authentication:

```bash
# Add remote with SSH
git remote add origin git@github.com:YOUR_USERNAME/sql-validator-tool.git

# Push
git branch -M main
git push -u origin main
```

## Repository Structure

After pushing, your repository will contain:

```
sql-validator-tool/
├── .gitignore              # Git ignore rules
├── README.md               # Main documentation
├── GIT_SETUP.md           # This file
├── build.sh               # Build script
├── manifest.txt           # JAR manifest
├── sql-validator.jar      # Compiled JAR
├── src/                   # Source code
├── lib/                   # Dependencies (jsqlparser)
├── test-examples/         # Test files
├── config/                # Configuration
└── docs/                  # Documentation
    ├── USAGE_GUIDE.md
    ├── SYNTAX_ERROR_DETECTION.md
    ├── MULTI_ISSUE_HANDLING.md
    └── TECHNICAL_DEEP_DIVE.md
```

## Common Git Commands

### Check Status
```bash
git status
```

### View Commit History
```bash
git log --oneline
```

### Create a New Branch
```bash
git checkout -b feature/new-feature
```

### Push Changes
```bash
git add .
git commit -m "Description of changes"
git push
```

### Pull Latest Changes
```bash
git pull origin main
```

## Troubleshooting

### Authentication Issues

If you encounter authentication errors:

1. **HTTPS**: Use a Personal Access Token instead of password
   - Go to GitHub Settings → Developer settings → Personal access tokens
   - Generate new token with `repo` scope
   - Use token as password when prompted

2. **SSH**: Set up SSH keys
   ```bash
   ssh-keygen -t ed25519 -C "your_email@example.com"
   cat ~/.ssh/id_ed25519.pub
   # Add the output to GitHub Settings → SSH Keys
   ```

### Large Files

If you have large files (>100MB), consider using Git LFS:
```bash
git lfs install
git lfs track "*.jar"
git add .gitattributes
```

### Reset to Clean State

If you need to start over:
```bash
rm -rf .git
git init
# Then follow steps 2-5 again
```

## Next Steps

After pushing to GitHub:

1. Add repository description and topics on GitHub
2. Create releases for version tracking
3. Set up branch protection rules (optional)
4. Add collaborators if needed
5. Consider adding GitHub Actions for CI/CD

## Tags and Releases

To create a release:

```bash
# Create and push a tag
git tag -a v3.0.0 -m "Version 3.0.0: Syntax validation and multi-issue handling"
git push origin v3.0.0
```

Then create a release on GitHub using this tag.

## Support

For Git-related issues, refer to:
- [Git Documentation](https://git-scm.com/doc)
- [GitHub Guides](https://guides.github.com/)