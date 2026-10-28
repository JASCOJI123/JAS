import Foundation

enum MontajError: LocalizedError {
    case noClips
    case noVideoTrack
    case noAudioTrack
    case compositionFailed
    case exportFailed
    case photoAccessDenied

    var errorDescription: String? {
        switch self {
        case .noClips: return "Avval video qo'shing."
        case .noVideoTrack: return "Faylda video topilmadi."
        case .noAudioTrack: return "Faylda ovoz topilmadi."
        case .compositionFailed: return "Videoni yig'ib bo'lmadi."
        case .exportFailed: return "Videoni eksport qilib bo'lmadi."
        case .photoAccessDenied: return "Galereyaga saqlash uchun Sozlamalarda ruxsat bering."
        }
    }
}
