# Flatpak DevTools — Commercial & Billing Guide

**Product:** Flatpak DevTools (`io.github.andrepg.flatpak-support`)
**Business Model:** Freemium via JetBrains Marketplace
**Status:** Implemented in v2026.1.1
**Last Updated:** 2026-08-15

---

## 1. Executive Summary

Flatpak DevTools is a **freemium** plugin for JetBrains IDEs that brings first-class Flatpak and GNOME/GTK development workflows into the IDE. The plugin is **open-source (AGPLv3)** on GitHub, with a **commercial license** for the Marketplace binary that unlocks premium features.

### 1.1 Value Proposition

- **For Users:** Develop and package sandboxed Linux applications without leaving your IDE
- **For Business:** Sustainable revenue stream through subscription model while maintaining community trust through open source
- **For Ecosystem:** Professional-grade tooling that aligns with Linux/GNOME transparency values

### 1.2 Revenue Model at a Glance

| Metric | Value |
|---|---|
| **Pricing** | $4/month or $40/year |
| **Trial Period** | 30 days (automatic, no configuration needed) |
| **Vendor Share** | 85% (JetBrains takes 15% commission) |
| **Net Annual Revenue per Subscriber** | ~$34/year |
| **Payout Threshold** | $200/EUR 200 (paid annually if below threshold) |
| **Payout Frequency** | Monthly, within 30 days of month end |
| **Product Code** | `PFLATPAKDEV` (permanent) |
| **Release Version** | `20261` (anchors subscription calculations) |

---

## 2. Feature Tiers & Pricing

### 2.1 Tier Comparison

| Feature | Description | Free Tier | Premium Tier |
|---|---|---|---|
| Manifest auto-detection + schema completion/validation | JSON/YAML Flatpak manifests are recognized automatically and bound to the official Flatpak schema for code completion and inline validation | ✅ | ✅ |
| Flatpak run configurations | Build, Run, Export, Clean, Validate, and Custom commands as dedicated run configurations | ✅ | ✅ |
| Configurable binaries | Custom `flatpak` and `flatpak-builder` paths via Settings → Languages & Frameworks → Flatpak | ✅ | ✅ |
| GNOME SDK autodetection | Resolves the project's SDK/runtime from the manifest via flatpak CLI (install-root glob fallback) | ✅ | ✅ |
| .ui/.glade schema completion & validation | GtkBuilder XML files served the generated XSD from the project's GNOME SDK (bundled fallback) | ✅ | ✅ |
| Live GTK Preview | Tool window + in-editor *Preview* action rendering `.ui`/`.glade` files to an image inside the GNOME SDK | ❌ | ✅ |

### 2.2 Pricing Strategy

**Current Pricing:**
- **Monthly:** $4.00 USD
- **Annual:** $40.00 USD (16.7% discount vs monthly)

**Rationale:**
- **Accessible:** Low barrier to entry for individual developers
- **Competitive:** Comparable to other niche JetBrains plugins
- **Sustainable:** Generates sufficient revenue for continuous development
- **Psychological:** Annual pricing encourages longer commitments

**Revenue Projections:**
- 100 subscribers (annual): ~$3,400/year net
- 500 subscribers (annual): ~$17,000/year net
- 1,000 subscribers (annual): ~$34,000/year net

### 2.3 Feature Gating Philosophy

**Why GTK Preview is Premium:**
- The GTK Preview is the "wow" feature that demonstrates immediate value
- Visual feedback drives conversion and retention
- Schema completion stays free to maintain a compelling free tier
- Gating the preview doesn't cripple the core workflow

**Why Schema Stays Free:**
- Schema-driven completion is expected in modern IDEs
- Gating it would push users to build from source
- The free tier must be genuinely useful to drive adoption
- Premium features should be "nice to have" not "need to have"

---

## 3. Technical Implementation

### 3.1 Product Descriptor

```xml
<product-descriptor code="PFLATPAKDEV" release-date="20260814" release-version="20261" optional="true"/>
```

- `code`: **PFLATPAKDEV** — Permanent product code in JetBrains Sales System
- `release-date`: 20260814 — Anchors major release for subscription calculations
- `release-version`: 20261 — Must match calendar versioning scheme (2026.1.x)
- `optional="true"`: Required for freemium — plugin works without license

### 3.2 Versioning Strategy

**Calendar Versioning:** `2026.1.x`
- `2026`: Year of major release
- `1`: Major version within year
- `x`: Minor version (incremental updates)

**Rules:**
- Minor updates (2026.1.1 → 2026.1.2) keep `release-version`/`release-date` **frozen**
- Changing `release-version`/`release-date` = **new major release** (resets trials)
- Perpetual-fallback licensees continue receiving minor updates

### 3.3 License Verification Architecture

**Components:**
- `LicenseCheck.kt` — Kotlin port of JetBrains' `CheckLicense` (2024.3+)
  - Verifies stamp/key signatures against JetBrains root certificates
  - Returns `true`/`false`/`null` (null = not initialized)
  - Handles personal codes and floating license servers
- `PremiumFeatureGate.kt` — Single decision point
  - Treats `null` license state as **locked** (safe default)
  - Routes to registration dialog when access requested

**Gating Points:**
1. `GtkPreviewToolWindowFactory.createToolWindowContent` — Shows upgrade panel instead of preview
2. `GtkPreviewEditorNotificationProvider` — Preview action becomes "Upgrade to unlock Preview"

**Development Override:**
- `flatpak.devtools.development=true` system property
- Automatically set by `runIde` task
- Never set in release builds
- Allows full premium access during development

---

## 4. Financial Mechanics

### 4.1 Pricing & Commission

| Item | Details |
|---|---|
| **List Price (Monthly)** | $4.00 USD |
| **List Price (Annual)** | $40.00 USD |
| **JetBrains Commission** | 15% (capped at 25%) |
| **Vendor Net (Monthly)** | $3.40 USD |
| **Vendor Net (Annual)** | $34.00 USD |
| **Commission Change Notice** | 1 month minimum |

**Note:** VAT/WHT taxes are added on top of list price and processed by JetBrains; they do **not** reduce the vendor's share.

### 4.2 Payouts

**Schedule:**
- Monthly payouts
- Within 30 days of month end
- Accrues until threshold met

**Threshold:** $200 USD / EUR 200
- Paid annually even if below threshold at year end
- Requires bank details + tax forms in Marketplace profile **before first release**

**Example Timeline:**
- January sales: $150 → accrued
- February sales: $100 → total $250 → payout triggered
- Payout received: ~30 days after February ends

### 4.3 Revenue Recognition

- **Subscription Revenue:** Recognized monthly as earned
- **Trial Conversions:** Tracked but not guaranteed
- **Churn:** Monitor monthly for pricing adjustments
- **Refunds:** Handled by JetBrains per their policy

---

## 5. Market Analysis & Positioning

### 5.1 Target Audience

**Primary:**
- Professional Linux/GNOME developers using JetBrains IDEs
- Flatpak application developers
- GTK/Adwaita UI developers

**Secondary:**
- Open source maintainers (free tier)
- Students and hobbyists (free tier)
- Enterprise teams (volume licensing potential)

### 5.2 Competitive Landscape

| Competitor | Free | Paid | Notes |
|---|---|---|---|
| GNOME Builder | ✅ | ❌ | Native GTK app, no JetBrains integration |
| VS Code Extensions | ✅ | ❌ | Fragmented, no unified experience |
| Manual CLI | ✅ | ❌ | No IDE integration |
| **Flatpak DevTools** | ✅ | ✅ | **Only JetBrains-native solution** |

### 5.3 Unique Selling Points

1. **Native Integration** — First-class JetBrains IDE experience
2. **Schema-Driven** — Intelligent completion for manifests and UI files
3. **One-Click Workflows** — Dedicated run configurations for all Flatpak actions
4. **Dual-License** — Open source for trust, commercial for sustainability
5. **GTK Preview** — Visual feedback directly in the IDE (premium)

---

## 6. Business Decisions & Trade-offs

### 6.1 Open Source Strategy

**Why Open Source:**
- **Trust:** Linux developers expect transparency
- **Contributions:** Community can fix bugs and add features
- **Adoption:** Lower friction for evaluation
- **Ecosystem Fit:** Aligns with GNOME/Linux values

**Accepted Trade-offs:**
- Build-from-source bypass of premium features
- AGPL forks possible (mitigated by branding and update cadence)
- No binary secrecy (mitigated by convenience and support)

### 6.2 Pricing Decisions

**$4/month Rationale:**
- Low enough for individuals to justify
- High enough to be taken seriously
- Comparable to other niche plugins
- Allows volume discounts for teams

**Annual Discount (16.7%) Rationale:**
- Encourages longer commitments
- Reduces churn
- Predictable revenue
- Industry standard practice

### 6.3 Feature Gating Decisions

**Premium Features:**
- GTK Preview (visual, high-value)
- Future: GResource awareness
- Future: LSP for XML files

**Free Features:**
- All core Flatpak workflow
- All schema completion/validation
- All run configurations
- All settings

**Principle:** Free tier must be genuinely useful; premium adds convenience and "wow" factors.

---

## 7. Risk Assessment

| Risk | Impact | Probability | Mitigation |
|---|---|---|---|
| Low adoption | High | Medium | Marketing, community engagement, free tier value |
| High churn | Medium | Medium | Monitor metrics, adjust pricing, add value |
| Build-from-source bypass | Medium | High | Accepted; focus on convenience and updates |
| AGPL fork competition | Medium | Low | Branding, update cadence, Marketplace presence |
| JetBrains policy change | High | Low | Diversify distribution, monitor announcements |
| Commission increase | Medium | Low | Pricing adjustments, cost absorption |

---

## 8. Action Items & Next Steps

### 8.1 Immediate (Before Release)

- [x] Complete plugin development and testing
- [x] Set up Marketplace vendor profile
- [x] Configure bank details and tax forms
- [ ] Submit plugin for Marketplace review
- [ ] Set pricing ($4/mo, $40/yr)
- [ ] Upload screenshots and listing assets

### 8.2 Short-term (First 3 Months)

- [ ] Monitor adoption metrics
- [ ] Track trial-to-paid conversion rate
- [ ] Gather user feedback on pricing
- [ ] Adjust marketing messaging based on feedback
- [ ] Consider introductory pricing or promotions

### 8.3 Medium-term (3-12 Months)

- [ ] Evaluate pricing based on real data
- [ ] Consider team/enterprise pricing tiers
- [ ] Add more premium features (GResource, LSP)
- [ ] Explore volume licensing options
- [ ] Investigate ProGuard obfuscation for Marketplace build

### 8.4 Long-term (12+ Months)

- [ ] Expand to other JetBrains IDEs if demand exists
- [ ] Consider additional premium features
- [ ] Evaluate partnership opportunities
- [ ] Explore sponsorship models for open source projects

---

## 9. Metrics to Track

| Metric | Target | Measurement |
|---|---|---|
| Active Installations | 1,000+ (6 months) | Marketplace dashboard |
| Trial Start Rate | 20% of new installs | Marketplace analytics |
| Trial-to-Paid Conversion | 10-15% | Marketplace analytics |
| Monthly Active Users | 500+ (6 months) | Marketplace dashboard |
| Churn Rate | <5% monthly | Calculate from subscriptions |
| Revenue | $500+/month (6 months) | Marketplace payouts |
| Net Promoter Score | 40+ | User surveys |

---

## 10. Appendix

### 10.1 Glossary

- **AGPLv3:** Affero General Public License version 3 — copyleft license requiring source availability for network-use cases
- **Calendar Versioning:** Version scheme using year and sequential numbers (YYYY.MM.PATCH)
- **Freemium:** Business model offering free basic features with paid premium upgrades
- **JetBrains Marketplace:** Official plugin distribution platform for JetBrains IDEs
- **Product Code:** Unique identifier for paid plugins in JetBrains Sales System (PFLATPAKDEV)

### 10.2 References

- [JetBrains Marketplace Documentation](https://plugins.jetbrains.com/docs/marketplace.html)
- [AGPLv3 License Text](../LICENSE)
- [Plugin Source Code](https://github.com/andrepg/jetbrains-flatpak)
- [Flatpak Documentation](https://docs.flatpak.org/)

---

*This document guides commercial and billing decisions for Flatpak DevTools. For technical implementation details, see the main [BILLING.md](../BILLING.md) in the repository root.*
