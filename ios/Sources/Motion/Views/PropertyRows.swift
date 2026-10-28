import SwiftUI

/// ⏱ / ◆ tugmasi: keyframe yoqish va o'chirish.
struct KeyframeButton: View {
    let isAnimated: Bool
    let hasKeyframeHere: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: hasKeyframeHere ? "diamond.fill" : (isAnimated ? "diamond" : "stopwatch"))
                .foregroundStyle(isAnimated ? Color.yellow : Color.secondary)
                .frame(width: 26, height: 26)
        }
        .buttonStyle(.plain)
    }
}

struct EasingMenu: View {
    let current: Easing
    let onSelect: (Easing) -> Void

    var body: some View {
        Menu {
            ForEach(Easing.allCases) { easing in
                Button(easing.title) { onSelect(easing) }
            }
        } label: {
            Text(current.title)
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(Color.yellow.opacity(0.18), in: Capsule())
        }
    }
}

/// Animatsiya qilinadigan son xususiyati: slider + keyframe + easing.
struct AnimatableRow: View {
    let title: String
    @Binding var property: Animatable<Double>
    let time: Double
    let range: ClosedRange<Double>
    let format: (Double) -> String

    var body: some View {
        let keyframeIndex = property.keyframeIndex(at: time)
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                KeyframeButton(isAnimated: property.isAnimated, hasKeyframeHere: keyframeIndex != nil) {
                    property.toggleKeyframe(at: time)
                }
                Text(title).font(.subheadline)
                if let keyframeIndex {
                    EasingMenu(current: property.keyframes[keyframeIndex].easing) { property.setEasing($0, at: time) }
                }
                Spacer()
                Text(format(property.value(at: time)))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            Slider(
                value: Binding(
                    get: { min(max(property.value(at: time), range.lowerBound), range.upperBound) },
                    set: { property.set($0, at: time) }
                ),
                in: range
            )
        }
    }
}

/// Pozitsiya (X va Y bitta keyframe bilan).
struct PositionRow: View {
    @Binding var property: Animatable<Vec2>
    let time: Double
    let canvasSize: Size2

    var body: some View {
        let keyframeIndex = property.keyframeIndex(at: time)
        let value = property.value(at: time)
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                KeyframeButton(isAnimated: property.isAnimated, hasKeyframeHere: keyframeIndex != nil) {
                    property.toggleKeyframe(at: time)
                }
                Text("Joyi").font(.subheadline)
                if let keyframeIndex {
                    EasingMenu(current: property.keyframes[keyframeIndex].easing) { property.setEasing($0, at: time) }
                }
                Spacer()
                Text("\(Int(value.x)), \(Int(value.y))")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            axisSlider("X", value: value.x, limit: canvasSize.width) { x in
                property.set(Vec2(x: x, y: property.value(at: time).y), at: time)
            }
            axisSlider("Y", value: value.y, limit: canvasSize.height) { y in
                property.set(Vec2(x: property.value(at: time).x, y: y), at: time)
            }
        }
    }

    private func axisSlider(_ axis: String, value: Double, limit: Double, onChange: @escaping (Double) -> Void) -> some View {
        let range = (-limit * 0.5)...(limit * 1.5)
        return HStack {
            Text(axis).font(.caption).foregroundStyle(.secondary).frame(width: 14)
            Slider(
                value: Binding(get: { min(max(value, range.lowerBound), range.upperBound) }, set: onChange),
                in: range
            )
        }
    }
}

/// Oddiy (animatsiyasiz) son xususiyati.
struct ValueRow: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    var step: Double? = nil
    let format: (Double) -> String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(title).font(.subheadline)
                Spacer()
                Text(format(value)).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
            if let step {
                Slider(value: $value, in: range, step: step)
            } else {
                Slider(value: $value, in: range)
            }
        }
    }
}

extension RGBAColor {
    /// SwiftUI ColorPicker uchun.
    static func binding(_ color: Binding<RGBAColor>) -> Binding<Color> {
        Binding(get: { color.wrappedValue.color }, set: { color.wrappedValue = RGBAColor($0) })
    }
}

enum NumberFormat {
    static func percent(_ value: Double) -> String { "\(Int((value * 100).rounded()))%" }
    static func degrees(_ value: Double) -> String { "\(Int(value.rounded()))°" }
    static func pixels(_ value: Double) -> String { "\(Int(value.rounded())) px" }
    static func decimal(_ value: Double) -> String { String(format: "%.2f", value) }
    static func seconds(_ value: Double) -> String { String(format: "%.1f s", value) }
}
