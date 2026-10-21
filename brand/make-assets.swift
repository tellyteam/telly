// Draws every telly icon, banner and splash, for every app, from the one wordmark in
// brand/telly-wordmark.svg (Roboto Black, made by make-wordmark.swift).
// Run from ~/Code: swift SimpleIPTV/brand/make-assets.swift
import AppKit

let code = FileManager.default.currentDirectoryPath
let svg = try! String(contentsOfFile: "\(code)/SimpleIPTV/brand/telly-wordmark.svg", encoding: .utf8)
func attribute(_ name: String) -> String {
    let start = svg.range(of: "\(name)=\"")!.upperBound
    return String(svg[start..<svg[start...].firstIndex(of: "\"")!])
}
let box = attribute("viewBox").split(separator: " ").map { CGFloat(Double($0)!) }
let markW = box[2], markH = box[3]
let d = attribute(" d")

/// The wordmark's outline, from the SVG path (absolute M, L, Q, C, Z only), y up.
let mark: CGPath = {
    let p = CGMutablePath()
    let scanner = Scanner(string: d)
    scanner.charactersToBeSkipped = .whitespaces
    func n() -> CGFloat { CGFloat(scanner.scanDouble()!) }
    func pt() -> CGPoint { let x = n(); let y = n(); return CGPoint(x: x, y: markH - y) }
    while !scanner.isAtEnd {
        guard let c = scanner.scanCharacter() else { break }
        switch c {
        case "M": p.move(to: pt())
        case "L": p.addLine(to: pt())
        case "Q": let c1 = pt(); p.addQuadCurve(to: pt(), control: c1)
        case "C": let c1 = pt(), c2 = pt(); p.addCurve(to: pt(), control1: c1, control2: c2)
        case "Z": p.closeSubpath()
        default: break
        }
    }
    return p
}()

let accent = CGColor(srgbRed: 0x4f/255, green: 0xc3/255, blue: 0xf7/255, alpha: 1)
let slateTop = NSColor(srgbRed: 0x2a/255, green: 0x3a/255, blue: 0x55/255, alpha: 1)
let slateBottom = NSColor(srgbRed: 0x15/255, green: 0x1a/255, blue: 0x22/255, alpha: 1)

/// Draws the wordmark [width] wide, centred on (cx, cy); with [bar], the accent bar beneath it.
func drawMark(_ ctx: CGContext, cx: CGFloat, cy: CGFloat, width: CGFloat, bar: Bool) {
    let s = width / markW
    let h = markH * s
    let barH = bar ? h * 0.075 : 0, gap = bar ? h * 0.16 : 0
    let top = cy + (h + gap + barH) / 2
    ctx.saveGState()
    ctx.translateBy(x: cx - width / 2, y: top - h)
    ctx.scaleBy(x: s, y: s)
    ctx.addPath(mark)
    ctx.setFillColor(.white)
    ctx.fillPath()
    ctx.restoreGState()
    if bar {
        let bw = width * 0.3
        ctx.setFillColor(accent)
        ctx.addPath(CGPath(roundedRect: CGRect(x: cx - bw / 2, y: top - h - gap - barH, width: bw, height: barH),
                           cornerWidth: barH / 2, cornerHeight: barH / 2, transform: nil))
        ctx.fillPath()
    }
}

func render(_ w: Int, _ h: Int, opaque: Bool, to path: String, _ draw: (CGContext) -> Void) {
    let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpace(name: CGColorSpace.sRGB)!,
                        bitmapInfo: (opaque ? CGImageAlphaInfo.noneSkipLast : .premultipliedLast).rawValue)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
    draw(ctx)
    NSGraphicsContext.restoreGraphicsState()
    let data = NSBitmapImageRep(cgImage: ctx.makeImage()!).representation(using: .png, properties: [:])!
    try! data.write(to: URL(fileURLWithPath: "\(code)/\(path)"))
    print("wrote \(path) (\(w)×\(h))")
}

func slate(_ rect: NSRect, angle: CGFloat) {
    NSGradient(colors: [slateTop, slateBottom])!.draw(in: rect, angle: angle)
}

/// A full-bleed tile: slate gradient, wordmark and bar. iPad, LG and the Fire TV banner.
func tile(_ w: Int, _ h: Int, markWidth: CGFloat, to path: String) {
    render(w, h, opaque: true, to: path) { ctx in
        slate(NSRect(x: 0, y: 0, width: w, height: h), angle: w == h ? -60 : -30)
        drawMark(ctx, cx: CGFloat(w) / 2, cy: CGFloat(h) / 2, width: markWidth, bar: true)
    }
}

// ---- Mac: macOS's icon grid, an 824px rounded tile with a soft shadow ----
func macIcon(_ px: Int, to path: String) {
    render(px, px, opaque: false, to: path) { ctx in
        let s = CGFloat(px) / 1024
        ctx.scaleBy(x: s, y: s)
        drawMacIcon(ctx)
    }
}

func drawMacIcon(_ ctx: CGContext) {
    let tileRect = NSRect(x: 100, y: 100, width: 824, height: 824)
    let shape = NSBezierPath(roundedRect: tileRect, xRadius: 185, yRadius: 185)
    NSGraphicsContext.current!.saveGraphicsState()
    let shadow = NSShadow()
    shadow.shadowColor = NSColor(white: 0, alpha: 0.35); shadow.shadowBlurRadius = 24; shadow.shadowOffset = NSSize(width: 0, height: -10)
    shadow.set()
    slateBottom.setFill(); shape.fill()
    NSGraphicsContext.current!.restoreGraphicsState()
    NSGradient(colors: [slateTop, slateBottom])!.draw(in: shape, angle: -60)
    drawMark(ctx, cx: 512, cy: 512, width: 560, bar: true)
}

macIcon(1024, to: "telly-desktop/assets/icon.png")
// The native Mac app (built from telly-ipad): every size of the asset catalog's Mac icon.
let macSet = "telly-ipad/Telly/Assets.xcassets/AppIconMac.appiconset"
try! FileManager.default.createDirectory(atPath: "\(code)/\(macSet)", withIntermediateDirectories: true)
var macImages: [String] = []
for points in [16, 32, 128, 256, 512] {
    for scale in [1, 2] {
        let name = "icon_\(points)x\(points)\(scale == 2 ? "@2x" : "").png"
        macIcon(points * scale, to: "\(macSet)/\(name)")
        macImages.append(#"{ "filename" : "\#(name)", "idiom" : "mac", "scale" : "\#(scale)x", "size" : "\#(points)x\#(points)" }"#)
    }
}
try! """
{
  "images" : [
    \(macImages.joined(separator: ",\n    "))
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}

""".write(toFile: "\(code)/\(macSet)/Contents.json", atomically: true, encoding: .utf8)

// ---- iPad: full bleed, iPadOS rounds the corners ----
tile(1024, 1024, markWidth: 680, to: "telly-ipad/Telly/Assets.xcassets/AppIcon.appiconset/icon.png")

// ---- LG TV ----
tile(80, 80, markWidth: 58, to: "telly-desktop/webos/icon.png")
tile(130, 130, markWidth: 94, to: "telly-desktop/webos/largeIcon.png")
render(1920, 1080, opaque: true, to: "telly-desktop/webos/splash.png") { ctx in
    ctx.setFillColor(CGColor(srgbRed: 0x15/255, green: 0x1a/255, blue: 0x22/255, alpha: 1))
    ctx.fill(CGRect(x: 0, y: 0, width: 1920, height: 1080))
    drawMark(ctx, cx: 960, cy: 540, width: 520, bar: true)
}

// ---- Fire TV: banner tiles, the launcher icon (white on transparent; Fire OS adds its tile) and the splash ----
tile(320, 180, markWidth: 196, to: "SimpleIPTV/app/src/main/res/drawable-xhdpi/aab_telly_banner.png")
tile(480, 270, markWidth: 294, to: "SimpleIPTV/app/src/main/res/drawable-xxhdpi/aab_telly_banner.png")
for (dir, px) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    render(px, px, opaque: false, to: "SimpleIPTV/app/src/main/res/drawable-\(dir)/aaa_telly_icon.png") { ctx in
        drawMark(ctx, cx: CGFloat(px) / 2, cy: CGFloat(px) / 2, width: CGFloat(px) * 0.82, bar: false)
    }
}
render(560, 200, opaque: false, to: "SimpleIPTV/app/src/main/res/drawable-xhdpi/splash_wordmark.png") { ctx in
    drawMark(ctx, cx: 280, cy: 100, width: 330, bar: false)
}
