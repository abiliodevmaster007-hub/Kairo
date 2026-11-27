package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass 2 of Kairo Layout Engine: computes concrete (x, y, width, height) LayoutBounds
 * supporting Fill weights, grow/shrink, wrap flow lines, additive margins, and cross-axis alignment.
 */
final class DistributePass {
    private record ChildSpec(
            LayoutEngine.LayoutNode node,
            LayoutEngine.MeasuredSize measured,
            ComputedStyle.EdgeInsets margin,
            LayoutSize wSpec,
            LayoutSize hSpec,
            double minW,
            double maxW,
            double minH,
            double maxH,
            double flexGrow,
            double flexShrink
    ) {}

    Map<KairoId, LayoutBounds> execute(
            LayoutEngine.LayoutNode root,
            LayoutBounds rootBounds,
            Map<KairoId, LayoutEngine.MeasuredSize> measured) {
        Map<KairoId, LayoutBounds> bounds = new LinkedHashMap<>();
        distributeNode(root, rootBounds, measured, bounds);
        return bounds;
    }

    private void distributeNode(
            LayoutEngine.LayoutNode node,
            LayoutBounds selfBounds,
            Map<KairoId, LayoutEngine.MeasuredSize> measured,
            Map<KairoId, LayoutBounds> bounds) {
        bounds.put(node.id(), selfBounds);
        if (node.children().isEmpty()) {
            return;
        }

        ComputedStyle.EdgeInsets padding = LayoutStyleReader.padding(node);
        double gap = LayoutStyleReader.gap(node);
        LayoutEngine.Axis axis = LayoutStyleReader.axis(node);
        double innerX = selfBounds.x() + padding.left();
        double innerY = selfBounds.y() + padding.top();
        double innerW = Math.max(0, selfBounds.width() - padding.horizontal());
        double innerH = Math.max(0, selfBounds.height() - padding.vertical());

        if (axis == LayoutEngine.Axis.OVERLAY) {
            LayoutAlignment containerAlign = LayoutStyleReader.align(node, LayoutAlignment.START);
            for (LayoutEngine.LayoutNode child : node.children()) {
                ComputedStyle.EdgeInsets margin = LayoutStyleReader.margin(child);
                LayoutEngine.MeasuredSize cm = measured.get(child.id());
                double availW = Math.max(0, innerW - margin.horizontal());
                double availH = Math.max(0, innerH - margin.vertical());
                LayoutAlignment childAlign = LayoutStyleReader.align(child, containerAlign);
                double cw = resolveNonFlex(
                        LayoutStyleReader.size(child, "width",
                                childAlign == LayoutAlignment.STRETCH ? new LayoutSize.Fill(1) : new LayoutSize.Auto()),
                        cm.width(), availW, LayoutStyleReader.minWidth(child), LayoutStyleReader.maxWidth(child));
                double ch = resolveNonFlex(
                        LayoutStyleReader.size(child, "height",
                                childAlign == LayoutAlignment.STRETCH ? new LayoutSize.Fill(1) : new LayoutSize.Auto()),
                        cm.height(), availH, LayoutStyleReader.minHeight(child), LayoutStyleReader.maxHeight(child));

                double cx = alignOffset(innerX + margin.left(), availW, cw, childAlign);
                double cy = alignOffset(innerY + margin.top(), availH, ch, childAlign);
                distributeNode(child, new LayoutBounds(cx, cy, cw, ch), measured, bounds);
            }
            return;
        }

        boolean vertical = axis == LayoutEngine.Axis.VERTICAL;
        boolean wrap = LayoutStyleReader.wrap(node);
        double totalMain = vertical ? innerH : innerW;
        double totalCross = vertical ? innerW : innerH;
        LayoutAlignment defaultCrossAlign = vertical ? LayoutAlignment.STRETCH : LayoutAlignment.START;
        LayoutAlignment containerAlign = LayoutStyleReader.align(node, defaultCrossAlign);

        List<ChildSpec> specs = new ArrayList<>(node.children().size());
        for (LayoutEngine.LayoutNode child : node.children()) {
            LayoutEngine.MeasuredSize cm = measured.get(child.id());
            ComputedStyle.EdgeInsets margin = LayoutStyleReader.margin(child);
            LayoutSize wSpec = LayoutStyleReader.size(child, "width",
                    vertical && containerAlign == LayoutAlignment.STRETCH
                            ? new LayoutSize.Fill(1)
                            : new LayoutSize.Auto());
            LayoutSize hSpec = LayoutStyleReader.size(child, "height", new LayoutSize.Auto());
            double minW = LayoutStyleReader.minWidth(child);
            double maxW = LayoutStyleReader.maxWidth(child);
            double minH = LayoutStyleReader.minHeight(child);
            double maxH = LayoutStyleReader.maxHeight(child);

            LayoutSize mainSpec = vertical ? hSpec : wSpec;
            double grow = mainSpec instanceof LayoutSize.Fill fill
                    ? fill.weight()
                    : LayoutStyleReader.grow(child);
            double shrink = LayoutStyleReader.shrink(child);
            specs.add(new ChildSpec(child, cm, margin, wSpec, hSpec, minW, maxW, minH, maxH, grow, shrink));
        }

        if (wrap) {
            distributeWrapped(specs, vertical, innerX, innerY, totalMain, gap, containerAlign, measured, bounds);
            return;
        }

        distributeSingleLine(specs, vertical, innerX, innerY, totalMain, totalCross, gap, containerAlign, measured, bounds);
    }

    private void distributeSingleLine(
            List<ChildSpec> specs,
            boolean vertical,
            double innerX,
            double innerY,
            double totalMain,
            double totalCross,
            double gap,
            LayoutAlignment containerAlign,
            Map<KairoId, LayoutEngine.MeasuredSize> measured,
            Map<KairoId, LayoutBounds> bounds) {
        double fixedMainAndMargins = 0;
        double totalGrowWeight = 0;
        double totalShrinkWeighted = 0;
        double[] baseMains = new double[specs.size()];

        for (int i = 0; i < specs.size(); i++) {
            ChildSpec s = specs.get(i);
            LayoutSize mainSpec = vertical ? s.hSpec() : s.wSpec();
            double intrinsicMain = vertical ? s.measured().height() : s.measured().width();
            double minMain = vertical ? s.minH() : s.minW();
            double maxMain = vertical ? s.maxH() : s.maxW();
            double marginMain = vertical ? s.margin().vertical() : s.margin().horizontal();

            if (s.flexGrow() > 0) {
                totalGrowWeight += s.flexGrow();
                baseMains[i] = minMain;
                fixedMainAndMargins += minMain + marginMain;
            } else {
                double resolved = resolveNonFlex(mainSpec, intrinsicMain, totalMain, minMain, maxMain);
                baseMains[i] = resolved;
                fixedMainAndMargins += resolved + marginMain;
                totalShrinkWeighted += s.flexShrink() * Math.max(0, resolved - minMain);
            }
            if (i > 0) fixedMainAndMargins += gap;
        }

        double remaining = totalMain - fixedMainAndMargins;
        double[] finalMains = new double[specs.size()];
        if (remaining > 0 && totalGrowWeight > 0) {
            boolean[] frozen = new boolean[specs.size()];
            double rem = remaining;
            double weightSum = totalGrowWeight;
            for (int iter = 0; iter < specs.size() && rem > 1e-6 && weightSum > 0; iter++) {
                boolean clampedAny = false;
                for (int i = 0; i < specs.size(); i++) {
                    ChildSpec s = specs.get(i);
                    if (frozen[i] || s.flexGrow() <= 0) continue;
                    double maxMain = vertical ? s.maxH() : s.maxW();
                    double candidate = baseMains[i] + rem * (s.flexGrow() / weightSum);
                    if (Double.isFinite(maxMain) && candidate >= maxMain) {
                        finalMains[i] = Math.max(baseMains[i], maxMain);
                        rem -= (finalMains[i] - baseMains[i]);
                        weightSum -= s.flexGrow();
                        frozen[i] = true;
                        clampedAny = true;
                    }
                }
                if (!clampedAny) break;
            }
            for (int i = 0; i < specs.size(); i++) {
                ChildSpec s = specs.get(i);
                double minMain = vertical ? s.minH() : s.minW();
                double maxMain = vertical ? s.maxH() : s.maxW();
                if (s.flexGrow() > 0 && !frozen[i]) {
                    double share = (rem > 0 && weightSum > 0) ? rem * (s.flexGrow() / weightSum) : 0;
                    finalMains[i] = LayoutStyleReader.clampDimension(baseMains[i] + share, minMain, maxMain);
                } else if (s.flexGrow() <= 0) {
                    finalMains[i] = baseMains[i];
                }
            }
        } else if (remaining < 0 && totalShrinkWeighted > 0) {
            boolean[] frozen = new boolean[specs.size()];
            double budget = -remaining;
            double shrinkWeightSum = totalShrinkWeighted;
            for (int iter = 0; iter < specs.size() && budget > 1e-6 && shrinkWeightSum > 0; iter++) {
                boolean clampedAny = false;
                for (int i = 0; i < specs.size(); i++) {
                    ChildSpec s = specs.get(i);
                    if (frozen[i] || s.flexGrow() > 0 || s.flexShrink() <= 0) continue;
                    double minMain = vertical ? s.minH() : s.minW();
                    double reducible = Math.max(0, baseMains[i] - minMain);
                    double share = budget * ((s.flexShrink() * reducible) / shrinkWeightSum);
                    if (share >= reducible) {
                        finalMains[i] = minMain;
                        budget -= reducible;
                        shrinkWeightSum -= s.flexShrink() * reducible;
                        frozen[i] = true;
                        clampedAny = true;
                    }
                }
                if (!clampedAny) break;
            }
            for (int i = 0; i < specs.size(); i++) {
                ChildSpec s = specs.get(i);
                double minMain = vertical ? s.minH() : s.minW();
                if (s.flexGrow() > 0) {
                    finalMains[i] = baseMains[i];
                } else if (!frozen[i] && s.flexShrink() > 0 && shrinkWeightSum > 0 && budget > 0) {
                    double reducible = Math.max(0, baseMains[i] - minMain);
                    double share = budget * ((s.flexShrink() * reducible) / shrinkWeightSum);
                    finalMains[i] = Math.max(minMain, baseMains[i] - share);
                } else if (!frozen[i]) {
                    finalMains[i] = baseMains[i];
                }
            }
        } else {
            System.arraycopy(baseMains, 0, finalMains, 0, specs.size());
        }

        double cursor = vertical ? innerY : innerX;
        for (int i = 0; i < specs.size(); i++) {
            ChildSpec s = specs.get(i);
            LayoutSize crossSpec = vertical ? s.wSpec() : s.hSpec();
            double intrinsicCross = vertical ? s.measured().width() : s.measured().height();
            double minCross = vertical ? s.minW() : s.minH();
            double maxCross = vertical ? s.maxW() : s.maxH();
            double availCross = Math.max(0, totalCross - (vertical ? s.margin().horizontal() : s.margin().vertical()));
            LayoutAlignment childAlign = LayoutStyleReader.align(s.node(), containerAlign);
            double cross = resolveNonFlex(crossSpec, intrinsicCross, availCross, minCross, maxCross);

            double mainPos = cursor + (vertical ? s.margin().top() : s.margin().left());
            double crossOrigin = (vertical ? innerX : innerY) + (vertical ? s.margin().left() : s.margin().top());
            double crossPos = alignOffset(crossOrigin, availCross, cross, childAlign);

            double cx = vertical ? crossPos : mainPos;
            double cy = vertical ? mainPos : crossPos;
            double cw = vertical ? cross : finalMains[i];
            double ch = vertical ? finalMains[i] : cross;

            distributeNode(s.node(), new LayoutBounds(cx, cy, cw, ch), measured, bounds);
            cursor += (vertical ? s.margin().vertical() : s.margin().horizontal()) + finalMains[i] + gap;
        }
    }

    private void distributeWrapped(
            List<ChildSpec> specs,
            boolean vertical,
            double innerX,
            double innerY,
            double totalMain,
            double gap,
            LayoutAlignment containerAlign,
            Map<KairoId, LayoutEngine.MeasuredSize> measured,
            Map<KairoId, LayoutBounds> bounds) {
        List<List<ChildSpec>> lines = new ArrayList<>();
        List<ChildSpec> currentLine = new ArrayList<>();
        double currentUsedMain = 0;

        for (ChildSpec s : specs) {
            LayoutSize mainSpec = vertical ? s.hSpec() : s.wSpec();
            double intrinsicMain = vertical ? s.measured().height() : s.measured().width();
            double minMain = vertical ? s.minH() : s.minW();
            double maxMain = vertical ? s.maxH() : s.maxW();
            double itemMain = resolveNonFlex(mainSpec, intrinsicMain, totalMain, minMain, maxMain)
                    + (vertical ? s.margin().vertical() : s.margin().horizontal());
            double nextUsed = currentLine.isEmpty() ? itemMain : currentUsedMain + gap + itemMain;

            if (!currentLine.isEmpty() && Double.isFinite(totalMain) && nextUsed > totalMain + 0.5) {
                lines.add(currentLine);
                currentLine = new ArrayList<>();
                currentLine.add(s);
                currentUsedMain = itemMain;
            } else {
                currentLine.add(s);
                currentUsedMain = nextUsed;
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }

        double crossCursor = vertical ? innerX : innerY;
        for (List<ChildSpec> line : lines) {
            double lineMaxCross = 0;
            for (ChildSpec s : line) {
                LayoutSize crossSpec = vertical ? s.wSpec() : s.hSpec();
                double intrinsicCross = vertical ? s.measured().width() : s.measured().height();
                double minCross = vertical ? s.minW() : s.minH();
                double maxCross = vertical ? s.maxW() : s.maxH();
                double crossVal = resolveNonFlex(crossSpec, intrinsicCross, Double.POSITIVE_INFINITY, minCross, maxCross)
                        + (vertical ? s.margin().horizontal() : s.margin().vertical());
                lineMaxCross = Math.max(lineMaxCross, crossVal);
            }

            double mainCursor = vertical ? innerY : innerX;
            for (ChildSpec s : line) {
                double availMain = totalMain;
                double availLineCross = Math.max(0, lineMaxCross - (vertical ? s.margin().horizontal() : s.margin().vertical()));
                double w = resolveNonFlex(s.wSpec(), s.measured().width(), vertical ? availLineCross : availMain, s.minW(), s.maxW());
                double h = resolveNonFlex(s.hSpec(), s.measured().height(), vertical ? availMain : availLineCross, s.minH(), s.maxH());
                double mainSize = vertical ? h : w;
                double crossSize = vertical ? w : h;

                LayoutAlignment childAlign = LayoutStyleReader.align(s.node(), containerAlign);
                double crossOrigin = crossCursor + (vertical ? s.margin().left() : s.margin().top());
                double crossPos = alignOffset(crossOrigin, availLineCross, crossSize, childAlign);
                double mainPos = mainCursor + (vertical ? s.margin().top() : s.margin().left());

                double cx = vertical ? crossPos : mainPos;
                double cy = vertical ? mainPos : crossPos;
                distributeNode(s.node(), new LayoutBounds(cx, cy, w, h), measured, bounds);
                mainCursor += mainSize + (vertical ? s.margin().vertical() : s.margin().horizontal()) + gap;
            }
            crossCursor += lineMaxCross + gap;
        }
    }

    private static double resolveNonFlex(
            LayoutSize spec,
            double intrinsic,
            double available,
            double minVal,
            double maxVal) {
        double v;
        if (spec instanceof LayoutSize.Fixed fixed) {
            v = fixed.px();
        } else if (spec instanceof LayoutSize.Percent pct && Double.isFinite(available)) {
            v = available * pct.fraction();
        } else if (spec instanceof LayoutSize.Fill && Double.isFinite(available)) {
            v = available;
        } else {
            v = intrinsic;
        }
        return LayoutStyleReader.clampDimension(v, minVal, maxVal);
    }

    private static double alignOffset(double origin, double available, double size, LayoutAlignment alignment) {
        if (alignment == LayoutAlignment.CENTER) {
            return origin + Math.max(0, (available - size) / 2.0);
        }
        if (alignment == LayoutAlignment.END) {
            return origin + Math.max(0, available - size);
        }
        return origin;
    }
}
