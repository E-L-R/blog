import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Tldr } from './Tldr';

describe('Tldr', () => {
  it('shows nothing for a post from before the ai step', () => {
    const { container } = render(<Tldr post={{}} />);

    expect(container).toBeEmptyDOMElement();
  });

  it('shows that a new post is being summarised', () => {
    render(<Tldr post={{ tldr: null, tldrStatus: 'PENDING' }} />);

    expect(screen.getByText('Writing a summary…')).toBeInTheDocument();
  });

  it("keeps the previous version's TL;DR while the new one is written", () => {
    render(<Tldr post={{ tldr: 'Old summary.', tldrStatus: 'PENDING' }} />);

    expect(screen.getByText('Old summary.')).toBeInTheDocument();
    expect(screen.getByText('Updating…')).toBeInTheDocument();
  });

  it('shows a generated TL;DR', () => {
    render(<Tldr post={{ tldr: 'Summary.', tldrStatus: 'GENERATED' }} />);

    expect(screen.getByRole('complementary', { name: 'AI summary' })).toHaveTextContent('Summary.');
    expect(screen.queryByText('Updating…')).not.toBeInTheDocument();
  });

  it('says when no TL;DR could be written', () => {
    render(<Tldr post={{ tldr: null, tldrStatus: 'FAILED' }} />);

    expect(screen.getByText(/couldn't be written/)).toBeInTheDocument();
  });
});
