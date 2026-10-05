import { useEffect, useRef } from 'react'

/** Returns a ref to attach to a sentinel element; calls onIntersect when it scrolls into view. */
export function useInfiniteScrollSentinel(onIntersect: () => void, enabled: boolean) {
  const ref = useRef<HTMLDivElement | null>(null)
  const onIntersectRef = useRef(onIntersect)

  useEffect(() => {
    onIntersectRef.current = onIntersect
  })

  useEffect(() => {
    const node = ref.current
    if (!node || !enabled) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          onIntersectRef.current()
        }
      },
      { rootMargin: '400px' },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [enabled])

  return ref
}
