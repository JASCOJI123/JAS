import SwiftUI

struct TextPanel: View {
    @ObservedObject var viewModel: EditorViewModel
    @State private var editingID: TextOverlay.ID?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                editingID = viewModel.addOverlay()
            } label: {
                Label("Matn / subtitr qo'shish", systemImage: "textformat")
            }
            .buttonStyle(.borderedProminent)

            if viewModel.overlays.isEmpty {
                Text("Matn joriy vaqtdan boshlab 3 soniya ko'rinadi. Vaqtini keyin o'zgartirish mumkin.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            ForEach(viewModel.overlays) { overlay in
                Button {
                    editingID = overlay.id
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(overlay.text.isEmpty ? "(bo'sh)" : overlay.text)
                                .lineLimit(1)
                                .foregroundStyle(.primary)
                            Text("\(Format.seconds(overlay.start)) – \(Format.seconds(overlay.end)) · \(overlay.position.title)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(.secondary)
                    }
                    .padding(12)
                    .background(Color.gray.opacity(0.15), in: RoundedRectangle(cornerRadius: 10))
                }
            }
        }
        .sheet(item: Binding(
            get: { editingID.map(EditingOverlay.init) },
            set: { editingID = $0?.id }
        )) { editing in
            if let overlay = viewModel.binding(forOverlay: editing.id) {
                TextOverlayEditor(
                    overlay: overlay,
                    maxTime: max(viewModel.totalDuration, 0.5),
                    onDelete: {
                        editingID = nil
                        viewModel.deleteOverlay(editing.id)
                    }
                )
            }
        }
    }
}

private struct EditingOverlay: Identifiable {
    let id: TextOverlay.ID
}

private struct TextOverlayEditor: View {
    @Binding var overlay: TextOverlay
    let maxTime: Double
    let onDelete: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Matn") {
                    TextField("Matn kiriting", text: $overlay.text, axis: .vertical)
                        .lineLimit(1...4)
                }

                Section("Ko'rinish vaqti") {
                    timeSlider(
                        title: "Boshlanishi",
                        value: Binding(
                            get: { min(overlay.start, maxTime) },
                            set: { overlay.start = min($0, overlay.end - 0.2) }
                        )
                    )
                    timeSlider(
                        title: "Tugashi",
                        value: Binding(
                            get: { min(overlay.end, maxTime) },
                            set: { overlay.end = max($0, overlay.start + 0.2) }
                        )
                    )
                }

                Section("Uslub") {
                    Picker("Joyi", selection: $overlay.position) {
                        ForEach(TextPosition.allCases) { Text($0.title).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    VStack(alignment: .leading) {
                        Text("O'lcham: \(Int(overlay.fontSize))")
                        Slider(value: $overlay.fontSize, in: 36...140, step: 2)
                    }

                    HStack(spacing: 14) {
                        ForEach(TextColor.allCases) { color in
                            Circle()
                                .fill(Color(color.uiColor))
                                .frame(width: 32, height: 32)
                                .overlay(Circle().stroke(Color.gray, lineWidth: 1))
                                .overlay(
                                    Circle()
                                        .stroke(Color.accentColor, lineWidth: overlay.color == color ? 3 : 0)
                                        .padding(-4)
                                )
                                .onTapGesture { overlay.color = color }
                        }
                    }
                    .padding(.vertical, 4)

                    Toggle("Orqa fon (subtitr uslubi)", isOn: $overlay.hasBackground)
                }

                Section {
                    Button("Matnni o'chirish", role: .destructive, action: onDelete)
                }
            }
            .navigationTitle("Matn")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Tayyor") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func timeSlider(title: String, value: Binding<Double>) -> some View {
        VStack(alignment: .leading) {
            Text("\(title): \(Format.seconds(value.wrappedValue))")
            Slider(value: value, in: 0...maxTime)
        }
    }
}
