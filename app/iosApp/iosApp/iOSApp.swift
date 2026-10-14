import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Invite links (kinzo://invite/CODE). Sign-in callbacks go straight to the
                // sign-in sheet, which listens for them itself.
                .onOpenURL { url in
                    _ = DeepLinksKt.handleDeepLink(url: url.absoluteString)
                }
        }
    }
}
