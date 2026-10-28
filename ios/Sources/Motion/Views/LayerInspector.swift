import SwiftUI

enum InspectorTab: String, CaseIterable, Identifiable {
    case transform, content, effects, mask, layer

    var id: String { rawValue }

    var title: String {
        switch self {
        case .transform: return "Transform"
        case .content: return "Kontent"
        case .effects: return "Effekt"
        case .mask: return "Mask"
        case .layer: return "Qatlam"
        }
    }
}

struct LayerInspector: View {
    @ObservedObject var viewModel: MotionEditorViewModel
    @Binding var layer: MotionLayer
    @State private var tab: InspectorTab = .transform

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Picker("Bo'lim", selection: $tab) {
                ForEach(InspectorTab.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)

            switch tab {
            case .transform: transform
            case .content: ContentInspector(layer: $layer)
            case .effects: EffectsInspector(layer: $layer, time: viewModel.currentTime)
            case .mask: MaskInspector(layer: $layer)
            case .layer: layerSettings
            }
        }
    }

    private var transform: some View {
        let time = viewModel.currentTime
        return VStack(alignment: .leading, spacing: 12) {
            PositionRow(property: $layer.position, time: time, canvasSize: viewModel.project.canvasSize)
            AnimatableRow(title: "O'lcham", property: $layer.scale, time: time, range: 0.02...4, format: NumberFormat.percent)
            AnimatableRow(title: "Burilish", property: $layer.rotation, time: time, range: -360...360, format: NumberFormat.degrees)
            AnimatableRow(title: "Shaffoflik", property: $layer.opacity, time: time, range: 0...1, format: NumberFormat.percent)
            Text("Maslahat: ko'rish oynasida qatlamni barmoq bilan suring, ikki barmoq bilan kattalashtiring yoki buring.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var layerSettings: some View {
        let duration = viewModel.project.duration
        return VStack(alignment: .leading, spacing: 12) {
            TextField("Nomi", text: $layer.name)
                .textFieldStyle(.roundedBorder)

            HStack {
                Text("Blend mode").font(.subheadline)
                Spacer()
                Picker("Blend mode", selection: $layer.blendMode) {
                    ForEach(BlendMode.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.menu)
            }

            ValueRow(
                title: "Boshlanishi (In)",
                value: Binding(get: { layer.inPoint }, set: { layer.inPoint = min($0, layer.outPoint - 0.1) }),
                range: 0...duration,
                format: NumberFormat.seconds
            )
            ValueRow(
                title: "Tugashi (Out)",
                value: Binding(get: { min(layer.outPoint, duration) }, set: { layer.outPoint = max($0, layer.inPoint + 0.1) }),
                range: 0...duration,
                format: NumberFormat.seconds
            )

            HStack {
                Button { viewModel.moveLayer(layer.id, by: 1) } label: { Label("Oldinga", systemImage: "square.2.layers.3d.top.filled") }
                Button { viewModel.moveLayer(layer.id, by: -1) } label: { Label("Orqaga", systemImage: "square.2.layers.3d.bottom.filled") }
            }
            .buttonStyle(.bordered)

            HStack {
                Button { viewModel.duplicateLayer(layer.id) } label: { Label("Nusxa", systemImage: "plus.square.on.square") }
                Spacer()
                Button(role: .destructive) { viewModel.deleteLayer(layer.id) } label: { Label("O'chirish", systemImage: "trash") }
            }
            .buttonStyle(.bordered)
        }
    }
}

// MARK: - Kontent

private struct ContentInspector: View {
    @Binding var layer: MotionLayer

    var body: some View {
        switch layer.content {
        case .text:
            TextContentEditor(text: Binding(
                get: { if case .text(let text) = layer.content { return text } else { return TextContent() } },
                set: { layer.content = .text($0) }
            ))
        case .shape(let current):
            ShapeContentEditor(shape: Binding(
                get: { if case .shape(let shape) = layer.content { return shape } else { return current } },
                set: { layer.content = .shape($0) }
            ))
        case .video(let current):
            ValueRow(
                title: "Ovoz balandligi",
                value: Binding(
                    get: { Double(layer.videoSource?.volume ?? current.volume) },
                    set: { newValue in
                        var source = layer.videoSource ?? current
                        source.volume = Float(newValue)
                        layer.content = .video(source)
                    }
                ),
                range: 0...1,
                format: NumberFormat.percent
            )
        case .image(let source):
            Text("Rasm: \(Int(source.size.width))×\(Int(source.size.height)) px")
                .foregroundStyle(.secondary)
        }
    }
}

private struct TextContentEditor: View {
    @Binding var text: TextContent

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Matn", text: $text.text, axis: .vertical)
                .lineLimit(1...4)
                .textFieldStyle(.roundedBorder)
            ValueRow(title: "Shrift o'lchami", value: $text.fontSize, range: 30...300, step: 2, format: NumberFormat.pixels)
            Toggle("Qalin", isOn: $text.isBold)
            Toggle("Soya", isOn: $text.hasShadow)
            ColorPicker("Rang", selection: RGBAColor.binding($text.color))

            Divider()
            Text("Matn animatsiyasi").font(.headline)
            Picker("Animatsiya", selection: $text.animation) {
                ForEach(TextAnimation.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.menu)
            if text.animation != .none && text.animation != .wave {
                ValueRow(title: "Davomiyligi", value: $text.animationDuration, range: 0.2...5, step: 0.1, format: NumberFormat.seconds)
            }
            Text("Animatsiya qatlam boshlanganda (In) ishga tushadi.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

private struct ShapeContentEditor: View {
    @Binding var shape: ShapeContent

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Shakl", selection: $shape.kind) {
                ForEach(ShapeKind.allCases) { kind in
                    Image(systemName: kind.systemImage).tag(kind)
                }
            }
            .pickerStyle(.segmented)
            ValueRow(title: "Eni", value: $shape.width, range: 10...2160, format: NumberFormat.pixels)
            ValueRow(title: "Bo'yi", value: $shape.height, range: 10...3840, format: NumberFormat.pixels)
            ColorPicker("To'ldirish rangi", selection: RGBAColor.binding($shape.fill))
            ValueRow(title: "Chegara qalinligi", value: $shape.strokeWidth, range: 0...80, format: NumberFormat.pixels)
            if shape.strokeWidth > 0 {
                ColorPicker("Chegara rangi", selection: RGBAColor.binding($shape.strokeColor))
            }
        }
    }
}

// MARK: - Effektlar

private struct EffectsInspector: View {
    @Binding var layer: MotionLayer
    let time: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Menu {
                ForEach(EffectKind.allCases) { kind in
                    Button(kind.title) { layer.effects.append(LayerEffect(kind: kind)) }
                }
            } label: {
                Label("Effekt qo'shish", systemImage: "wand.and.stars")
            }
            .buttonStyle(.borderedProminent)

            if layer.effects.isEmpty {
                Text("Effektlar tartib bilan qo'llanadi. Miqdorini ham keyframe bilan animatsiya qilish mumkin.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            ForEach($layer.effects) { $effect in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Toggle(effect.kind.title, isOn: $effect.isEnabled)
                            .font(.subheadline.weight(.semibold))
                        Button(role: .destructive) {
                            layer.effects.removeAll { $0.id == effect.id }
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                    }
                    AnimatableRow(
                        title: "Miqdor",
                        property: $effect.amount,
                        time: time,
                        range: effect.kind.range,
                        format: NumberFormat.decimal
                    )
                }
                .padding(12)
                .background(Color.gray.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }
}

// MARK: - Maska

private struct MaskInspector: View {
    @Binding var layer: MotionLayer

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if layer.mask == nil {
                Button {
                    layer.mask = LayerMask()
                } label: {
                    Label("Maska qo'shish", systemImage: "circle.dashed.inset.filled")
                }
                .buttonStyle(.borderedProminent)
                Text("Maska qatlamning faqat bir qismini ko'rsatadi. Teskari qilsangiz, aksincha o'sha qismni yashiradi.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Picker("Shakl", selection: maskBinding(\.shape)) {
                    ForEach(MaskShape.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
                ValueRow(title: "Chapdan (X)", value: maskBinding(\.x), range: -0.5...1, format: NumberFormat.percent)
                ValueRow(title: "Tepadan (Y)", value: maskBinding(\.y), range: -0.5...1, format: NumberFormat.percent)
                ValueRow(title: "Eni", value: maskBinding(\.width), range: 0.02...1.5, format: NumberFormat.percent)
                ValueRow(title: "Bo'yi", value: maskBinding(\.height), range: 0.02...1.5, format: NumberFormat.percent)
                ValueRow(title: "Feather (yumshoqlik)", value: maskBinding(\.feather), range: 0...200, format: NumberFormat.pixels)
                Toggle("Teskari (Inverted)", isOn: maskBinding(\.isInverted))
                Button(role: .destructive) { layer.mask = nil } label: {
                    Label("Maskani olib tashlash", systemImage: "trash")
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private func maskBinding<Value>(_ keyPath: WritableKeyPath<LayerMask, Value>) -> Binding<Value> {
        Binding(
            get: { (layer.mask ?? LayerMask())[keyPath: keyPath] },
            set: { newValue in
                var mask = layer.mask ?? LayerMask()
                mask[keyPath: keyPath] = newValue
                layer.mask = mask
            }
        )
    }
}
