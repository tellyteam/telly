// Draws telly's Android TV / Fire TV launcher banner: the white wordmark on blue-slate, 16:9.
// Run: swift assets/make-banner.swift <width> <out.png>   (320 for xhdpi, 480 for xxhdpi)
import AppKit

let w = CGFloat(Double(CommandLine.arguments[1])!)
let h = (w * 9 / 16).rounded()
let out = CommandLine.arguments[2]
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(w), pixelsHigh: Int(h), bitsPerSample: 8,
                           samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

// Full-bleed: the launcher draws its own rounded corners and focus ring.
let gradient = NSGradient(colors: [NSColor(srgbRed: 0x2a/255, green: 0x3a/255, blue: 0x55/255, alpha: 1),
                                   NSColor(srgbRed: 0x15/255, green: 0x1a/255, blue: 0x22/255, alpha: 1)])!
gradient.draw(in: NSRect(x: 0, y: 0, width: w, height: h), angle: -30)

let font = NSFont.systemFont(ofSize: h * 0.42, weight: .black)
let text = NSAttributedString(string: "telly", attributes: [.font: font, .foregroundColor: NSColor.white, .kern: -h * 0.004])
let b = text.size()
text.draw(at: NSPoint(x: (w - b.width) / 2, y: (h - b.height) / 2 + h * 0.03))
NSColor(srgbRed: 0x4f/255, green: 0xc3/255, blue: 0xf7/255, alpha: 1).setFill()
let barW = w * 0.14, barH = max(2, h * 0.03)
NSBezierPath(roundedRect: NSRect(x: (w - barW) / 2, y: h * 0.2, width: barW, height: barH), xRadius: barH / 2, yRadius: barH / 2).fill()

NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
