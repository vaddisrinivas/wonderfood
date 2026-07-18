# WonderFood Demo Script

Target: 20-30 seconds, silent GIF for README and 1080p MP4 for social posts.

## Story

1. Hook: "Your food data should not live in six apps."
2. Workspace: show Today as the daily command center.
3. Proof: show inventory and expiry-aware kitchen context.
4. Action: show shopping and receipt flow.
5. Trust: reviewable AI proposals, local-first defaults, open source.

## Voiceover

Meet WonderFood. A local-first food workspace for pantry, recipes, meals, shopping, receipts, and nutrition.

Plan the day from one Android screen.

Keep inventory useful with lots, expiry, prices, notes, and nutrition confidence.

Turn shopping and receipts into reviewable changes.

AI can propose. The app validates. You decide what gets saved.

## Render Commands

```bash
python3 /Users/srinivasvaddi/Projects/framecraft/framecraft.py render docs/marketing/framecraft-demo.json --output docs/images/wonderfood-demo.mp4 --auto-duration
ffmpeg -y -i docs/images/wonderfood-demo.mp4 -vf "fps=12,scale=640:360:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" docs/images/wonderfood-demo.gif
```

## Acceptance Checklist

- Demo data is synthetic and visually useful.
- GIF is under 5 MB for README use.
- AI copy says "proposal", "review", or "validate"; avoid magic/autopilot claims.
- Optional Google Drive and Health Connect are not framed as required.
- No personal food, receipt, health, provider, or account data is visible.
