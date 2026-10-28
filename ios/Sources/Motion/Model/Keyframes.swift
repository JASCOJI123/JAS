import Foundation

/// Ikki qiymat orasida oraliq qiymat hisoblash mumkin bo'lgan turlar.
protocol Interpolatable: Hashable {
    static func lerp(_ from: Self, _ to: Self, _ progress: Double) -> Self
}

extension Double: Interpolatable {
    static func lerp(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * progress
    }
}

struct Vec2: Interpolatable {
    var x: Double
    var y: Double

    static func lerp(_ from: Vec2, _ to: Vec2, _ progress: Double) -> Vec2 {
        Vec2(x: Double.lerp(from.x, to.x, progress), y: Double.lerp(from.y, to.y, progress))
    }
}

enum Easing: String, CaseIterable, Identifiable, Hashable {
    case linear, easeIn, easeOut, easeInOut, back, hold

    var id: String { rawValue }

    var title: String {
        switch self {
        case .linear: return "Chiziqli"
        case .easeIn: return "Ease In"
        case .easeOut: return "Ease Out"
        case .easeInOut: return "Easy Ease"
        case .back: return "Overshoot"
        case .hold: return "Hold"
        }
    }

    func apply(_ t: Double) -> Double {
        switch self {
        case .linear: return t
        case .easeIn: return t * t * t
        case .easeOut: return 1 - pow(1 - t, 3)
        case .easeInOut: return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
        case .back:
            let c1 = 1.70158
            let c3 = c1 + 1
            return 1 + c3 * pow(t - 1, 3) + c1 * pow(t - 1, 2)
        case .hold: return 0
        }
    }
}

struct Keyframe<Value: Interpolatable>: Identifiable, Hashable {
    let id: UUID
    /// Kompozitsiya vaqti (soniya).
    var time: Double
    var value: Value
    /// Shu keyframe'dan keyingisigacha bo'lgan harakat egri chizig'i.
    var easing: Easing

    init(time: Double, value: Value, easing: Easing = .easeInOut) {
        self.id = UUID()
        self.time = time
        self.value = value
        self.easing = easing
    }
}

/// After Effects'dagi kabi animatsiya qilinadigan xususiyat:
/// keyframe bo'lmasa doimiy qiymat, bo'lsa vaqt bo'yicha hisoblanadi.
struct Animatable<Value: Interpolatable>: Hashable {
    /// Bir kadr (30 fps) ichidagi keyframe "shu vaqtdagi" hisoblanadi.
    static var timeTolerance: Double { 1.0 / 60.0 }

    var constant: Value
    private(set) var keyframes: [Keyframe<Value>] = []

    init(_ value: Value) {
        self.constant = value
    }

    var isAnimated: Bool { !keyframes.isEmpty }

    func value(at time: Double) -> Value {
        guard let first = keyframes.first, let last = keyframes.last else { return constant }
        if time <= first.time { return first.value }
        if time >= last.time { return last.value }
        guard let index = keyframes.lastIndex(where: { $0.time <= time }), index + 1 < keyframes.count else {
            return last.value
        }
        let from = keyframes[index]
        let to = keyframes[index + 1]
        if from.easing == .hold { return from.value }
        let span = to.time - from.time
        let progress = span > 0 ? (time - from.time) / span : 1
        return Value.lerp(from.value, to.value, from.easing.apply(progress))
    }

    func keyframeIndex(at time: Double) -> Int? {
        keyframes.firstIndex { abs($0.time - time) <= Self.timeTolerance }
    }

    /// Animatsiya bo'lsa shu vaqtda keyframe yaratadi yoki yangilaydi, aks holda doimiy qiymatni o'zgartiradi.
    mutating func set(_ value: Value, at time: Double) {
        guard isAnimated else {
            constant = value
            return
        }
        if let index = keyframeIndex(at: time) {
            keyframes[index].value = value
        } else {
            insert(Keyframe(time: time, value: value))
        }
    }

    /// Sekundomer (⏱) tugmasi: shu vaqtda keyframe bo'lsa o'chiradi, bo'lmasa qo'shadi.
    mutating func toggleKeyframe(at time: Double) {
        if let index = keyframeIndex(at: time) {
            let removed = keyframes.remove(at: index)
            if keyframes.isEmpty { constant = removed.value }
        } else {
            insert(Keyframe(time: time, value: value(at: time)))
        }
    }

    mutating func setEasing(_ easing: Easing, at time: Double) {
        guard let index = keyframeIndex(at: time) else { return }
        keyframes[index].easing = easing
    }

    mutating func removeAllKeyframes(at time: Double) {
        constant = value(at: time)
        keyframes.removeAll()
    }

    /// Barcha keyframe'larni vaqt bo'yicha suradi (qatlam nusxasi yoki ko'chirish uchun).
    mutating func shift(by offset: Double) {
        for index in keyframes.indices { keyframes[index].time += offset }
    }

    var keyframeTimes: [Double] { keyframes.map(\.time) }

    private mutating func insert(_ keyframe: Keyframe<Value>) {
        keyframes.append(keyframe)
        keyframes.sort { $0.time < $1.time }
    }
}
