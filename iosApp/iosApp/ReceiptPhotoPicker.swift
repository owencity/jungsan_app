import SwiftUI
import UIKit
import PhotosUI
import AVFoundation
import ImageIO
import ComposeApp

/// 시스템 선택기가 한 장만 넘긴다. 전체 사진 보관함 접근 권한은 요청하지 않는다.
final class ReceiptPhotoPicker: NSObject, ObservableObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate, PHPickerViewControllerDelegate {
    private var active: UIViewController?
    private var generation = 0
    private let maxBytes = 8 * 1024 * 1024

    func start(_ source: String) {
        guard Thread.isMainThread else { DispatchQueue.main.async { self.start(source) }; return }
        guard active == nil else { return }
        generation += 1
        let request = generation
        if source == "CAMERA" {
            guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
                finish(message: "카메라를 사용할 수 없어요. 사진 선택이나 직접 입력을 이용해 주세요.", request: request); return
            }
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized: showCamera(request)
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    DispatchQueue.main.async {
                        guard request == self.generation else { return }
                        if granted { self.showCamera(request) }
                        else { self.finish(message: "카메라 권한이 없어요. 사진 선택이나 직접 입력을 이용해 주세요.", request: request) }
                    }
                }
            default: finish(message: "카메라 권한이 없어요. 설정에서 허용하거나 사진 선택을 이용해 주세요.", request: request)
            }
        } else {
            var configuration = PHPickerConfiguration()
            configuration.filter = .images
            configuration.selectionLimit = 1
            configuration.preferredAssetRepresentationMode = .current
            let picker = PHPickerViewController(configuration: configuration)
            picker.delegate = self
            present(picker, request: request)
        }
    }

    private func showCamera(_ request: Int) {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.allowsEditing = false
        picker.modalPresentationStyle = .fullScreen
        picker.delegate = self
        present(picker, request: request)
    }

    private func present(_ picker: UIViewController, request: Int) {
        guard request == generation else { return }
        var presenter = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }.first { $0.isKeyWindow }?.rootViewController
        while let next = presenter?.presentedViewController { presenter = next }
        guard let presenter = presenter else { finish(message: "사진 선택기를 열지 못했어요.", request: request); return }
        active = picker
        presenter.present(picker, animated: true)
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        finish(cancelled: true, request: generation)
    }

    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        let request = generation
        guard let image = info[.originalImage] as? UIImage else { finish(message: "사진을 읽지 못했어요.", request: request); return }
        picker.dismiss(animated: true)
        active = nil
        DispatchQueue.global(qos: .userInitiated).async {
            let ratio = min(1, 3200 / max(image.size.width, image.size.height))
            let size = CGSize(width: max(1,image.size.width * ratio), height: max(1,image.size.height * ratio))
            let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
            let normalized = UIGraphicsImageRenderer(size: size, format: format).image { context in
                UIColor.white.setFill(); context.fill(CGRect(origin: .zero,size: size))
                image.draw(in: CGRect(origin: .zero,size: size))
            }
            self.completeImage(normalized, request: request)
        }
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        let request = generation
        picker.dismiss(animated: true)
        active = nil
        guard let result = results.first else { finish(cancelled: true, request: request); return }
        result.itemProvider.loadFileRepresentation(forTypeIdentifier: "public.image") { url, error in
            guard error == nil, let url = url,
                  let attributes = try? FileManager.default.attributesOfItem(atPath: url.path),
                  let size = attributes[.size] as? NSNumber, size.intValue <= 32 * 1024 * 1024,
                  let data = try? Data(contentsOf: url),
                  let source = CGImageSourceCreateWithData(data as CFData, nil) else {
                self.finish(message: "사진을 읽지 못했어요. 다른 사진을 선택해 주세요.", request: request); return
            }
            let options: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways:true,
                kCGImageSourceCreateThumbnailWithTransform:true,kCGImageSourceThumbnailMaxPixelSize:3200]
            guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
                self.finish(message: "사진을 읽지 못했어요. 다른 사진을 선택해 주세요.", request: request); return
            }
            self.completeImage(UIImage(cgImage: image), request: request)
        }
    }

    private func completeImage(_ image: UIImage, request: Int) {
        guard let data = image.jpegData(compressionQuality: 0.95), !data.isEmpty, data.count <= maxBytes else {
            finish(message: "사진이 너무 커요. 다른 사진을 선택하거나 다시 촬영해 주세요.", request: request); return
        }
        finish(data: data, request: request)
    }

    private func finish(data: Data? = nil, cancelled: Bool = false, message: String? = nil, request: Int) {
        DispatchQueue.main.async {
            guard request == self.generation else { return }
            self.active?.dismiss(animated: true)
            self.active = nil
            IosReceiptPhotoBridge.shared.complete(jpegBase64: data?.base64EncodedString(), cancelled: cancelled, message: message)
        }
    }

    func cancel() {
        generation += 1
        active?.dismiss(animated: true)
        active = nil
    }
}
