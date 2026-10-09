import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { RichText } from './RichText'

function renderText(text: string) {
  return render(
    <MemoryRouter>
      <p data-testid="out">
        <RichText text={text} />
      </p>
    </MemoryRouter>,
  )
}

describe('RichText', () => {
  it('links mentions to profiles and hashtags to tag pages, keeping surrounding text', () => {
    renderText('hi @alice_1, loving #Sunset!')
    expect(screen.getByRole('link', { name: '@alice_1' })).toHaveAttribute('href', '/alice_1')
    expect(screen.getByRole('link', { name: '#Sunset' })).toHaveAttribute('href', '/explore/tags/sunset')
    expect(screen.getByTestId('out')).toHaveTextContent('hi @alice_1, loving #Sunset!')
  })

  it('leaves a trailing sentence dot outside the mention link', () => {
    renderText('thanks @bob.')
    expect(screen.getByRole('link', { name: '@bob' })).toHaveAttribute('href', '/bob')
    expect(screen.getByTestId('out')).toHaveTextContent('thanks @bob.')
  })

  it('does not link emails, short names or numeric-only hashtags', () => {
    renderText('mail foo@bar.com, hi @ab, #1 place')
    expect(screen.queryAllByRole('link')).toHaveLength(0)
    expect(screen.getByTestId('out')).toHaveTextContent('mail foo@bar.com, hi @ab, #1 place')
  })
})
