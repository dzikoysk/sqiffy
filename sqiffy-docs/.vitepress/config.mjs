import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "Sqiffy",
  description: "Experimental compound SQL framework with type-safe DSL API generated at compile-time from annotation based scheme diff",
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Docs', link: '/installation/about' }
    ],
    sidebar: [
      {
        text: 'Installation',
        items: [
          { text: 'About', link: '/installation/about' },
          { text: 'Gradle', link: '/installation/gradle' },
        ]
      },
      {
        text: 'Quick guide',
        items: [
          { text: 'Definining a schema', link: '/guide/definition' },
          { text: 'Interacting with DSL', link: '/guide/dsl' },
          { text: 'Migrations', link: '/guide/migrations' },
        ]
      },
      {
        text: 'Schema API',
        items: [
          { text: 'Entities', link: '/schema/entities' },
          { text: 'Enums', link: '/schema/enums' },
          { text: 'DTOs', link: '/schema/dto' },
        ]
      },
      {
        text: 'DSL API',
        items: [
          { text: 'Queries', link: '/dsl/queries' },
          { text: 'Dialects', link: '/dsl/dialects' },
          { text: 'Transactions', link: '/dsl/transactions' },
          { text: 'Conditions', link: '/dsl/conditions' },
          { text: 'Aggregations', link: '/dsl/aggregations' },
          { text: 'Joins', link: '/dsl/joins' },
        ]
      }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/dzikoysk/sqiffy' }
    ]
  }
})
