import SwiftUI

struct SitesView: View {
  @Environment(Store.self) var store
  @State private var cat = "全部"
  @State private var query = ""

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 0) {
          if store.data.accounts.isEmpty { empty } else {
            chips.padding(.top, 4)
            let groups = sections
            LazyVStack(alignment: .leading, spacing: 8, pinnedViews: []) {
              ForEach(groups, id: \.self) { c in
                let list = sectionItems(c)
                if !list.isEmpty {
                  HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(c).font(.system(size: 20, weight: .bold))
                    Text("\(list.count)").font(.system(size: 14, weight: .medium)).foregroundStyle(.secondary)
                  }.padding(.top, 18).padding(.leading, 4)
                  ForEach(list) { a in
                    Button { store.route = .account(a.id) } label: { ReelRow(account: a) }.buttonStyle(PressStyle(scale: 0.98))
                  }
                }
              }
            }
            .padding(.horizontal, 16)
            .animation(Motion.smooth, value: cat)
          }
          Color.clear.frame(height: 30)
        }
      }
      .scrollIndicators(.hidden)
      .navigationTitle("网站与 App")
      .searchable(text: $query, prompt: "搜索名称、域名、账号名")
      .toolbar { TopButtons() }
      .background(Glow().ignoresSafeArea())
    }
  }

  // Sections come straight from the store's prebuilt per-category lists (already name-sorted);
  // only a search query does any filtering, once per keystroke instead of once per category.
  var query_: String { query.trimmingCharacters(in: .whitespaces).lowercased() }
  func matches(_ a: Account, _ q: String) -> Bool {
    a.name.lowercased().contains(q) || a.domain.lowercased().contains(q) || (a.handle ?? "").lowercased().contains(q) || a.notes.lowercased().contains(q)
      || a.identityIds.compactMap { store.identity($0) }.contains { $0.value.lowercased().contains(q) || $0.label.lowercased().contains(q) }
  }
  func sectionItems(_ c: String) -> [Account] {
    let all = store.index.byCategory[c] ?? []
    let q = query_
    return q.isEmpty ? all : all.filter { matches($0, q) }
  }
  var cats: [String] { store.index.categories }
  var sections: [String] { cat == "全部" || !cats.contains(cat) ? cats : [cat] }

  var chips: some View {
    ScrollView(.horizontal) {
      HStack(spacing: 8) {
        ForEach(["全部"] + cats, id: \.self) { c in
          let n = c == "全部" ? store.data.accounts.count : (store.index.categoryCount[c] ?? 0)
          Button { withAnimation(Motion.snappy) { cat = c } } label: {
            HStack(spacing: 4) { Text(c); Text("\(n)").foregroundStyle(cat == c ? Color.white.opacity(0.75) : Color.secondary) }
              .font(.system(size: 14, weight: .semibold))
              .padding(.horizontal, 13).frame(height: 32)
              .foregroundStyle(cat == c ? Color.white : Color.primary)
              .background(cat == c ? AnyShapeStyle(Color.accentColor) : AnyShapeStyle(.glass), in: Capsule())
          }.buttonStyle(PressStyle(scale: 0.94))
        }
      }.padding(.horizontal, 16)
    }
    .scrollIndicators(.hidden)
    .sensoryFeedback(.selection, trigger: cat)
  }

  var empty: some View {
    VStack(spacing: 12) {
      Image(systemName: "square.grid.2x2").font(.system(size: 44, weight: .light)).foregroundStyle(.secondary)
      Text("还没有网站").font(.system(size: 20, weight: .bold))
      Text("记下一个网站或 App，选上它用的是哪个邮箱、哪个手机号。").font(.system(size: 15)).foregroundStyle(.secondary).multilineTextAlignment(.center)
      Button("添加网站或 App") { store.route = .picker(nil) }.buttonStyle(.borderedProminent).buttonBorderShape(.capsule).padding(.top, 6)
    }
    .padding(40).frame(maxWidth: .infinity).padding(.top, 60)
  }
}
