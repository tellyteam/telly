// Draws the telly wordmark once, from Roboto Black (Apache 2.0), as SVG paths: the single source
// for every app and icon. Letter spacing -0.02em, as the Fire TV app has always used.
// Run: swift brand/make-wordmark.swift path/to/Roboto-Black.ttf brand/telly-wordmark.svg
import CoreText
import Foundation

let fontURL = URL(fileURLWithPath: CommandLine.arguments[1]) as CFURL
let desc = (CTFontManagerCreateFontDescriptorsFromURL(fontURL) as! [CTFontDescriptor])[0]
let size: CGFloat = 1000
let font = CTFontCreateWithFontDescriptor(desc, size, nil)
let text = "telly"
let attr = NSAttributedString(string: text, attributes: [kCTFontAttributeName as NSAttributedString.Key: font,
                                                       kCTKernAttributeName as NSAttributedString.Key: -0.02 * size])
let line = CTLineCreateWithAttributedString(attr)
let path = CGMutablePath()
for run in CTLineGetGlyphRuns(line) as! [CTRun] {
    let n = CTRunGetGlyphCount(run)
    var glyphs = [CGGlyph](repeating: 0, count: n), positions = [CGPoint](repeating: .zero, count: n)
    CTRunGetGlyphs(run, CFRange(location: 0, length: n), &glyphs)
    CTRunGetPositions(run, CFRange(location: 0, length: n), &positions)
    for i in 0..<n {
        if let g = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
            path.addPath(g, transform: CGAffineTransform(translationX: positions[i].x, y: positions[i].y))
        }
    }
}
let box = path.boundingBoxOfPath
// SVG's y runs down: flip, and move the ink to the origin.
var t = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: -box.minX, ty: box.maxY)
let flipped = path.copy(using: &t)!
var d = ""
func f(_ v: CGFloat) -> String { String(format: "%.1f", v).replacingOccurrences(of: ".0", with: "") }
flipped.applyWithBlock { el in
    let p = el.pointee.points
    switch el.pointee.type {
    case .moveToPoint: d += "M\(f(p[0].x)) \(f(p[0].y))"
    case .addLineToPoint: d += "L\(f(p[0].x)) \(f(p[0].y))"
    case .addQuadCurveToPoint: d += "Q\(f(p[0].x)) \(f(p[0].y)) \(f(p[1].x)) \(f(p[1].y))"
    case .addCurveToPoint: d += "C\(f(p[0].x)) \(f(p[0].y)) \(f(p[1].x)) \(f(p[1].y)) \(f(p[2].x)) \(f(p[2].y))"
    case .closeSubpath: d += "Z"
    @unknown default: break
    }
}
let w = Int(box.width.rounded(.up)), h = Int(box.height.rounded(.up))
let svg = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 \(w) \(h)" width="\(w)" height="\(h)" role="img" aria-label="telly">
<path fill="currentColor" d="\(d)"/>
</svg>

"""
try! svg.write(toFile: CommandLine.arguments[2], atomically: true, encoding: .utf8)
print("\(w)x\(h), \(d.count) chars")
