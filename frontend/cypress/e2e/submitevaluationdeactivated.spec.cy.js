// UC-EVA-submit-evaluation 2a and 9a: the peer evaluation form and a deactivated team member. The API is stubbed, so
// these tests check what the form asks for and sends; the backend's own tests cover the refusal itself and that a
// teammate receives `enabled` for her. Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const envelope = (data) => ({ body: { flag: true, code: 200, message: 'Success', data } })

const member = (id, firstName, lastName, enabled) => ({
  id,
  username: `${firstName}@abc.edu`,
  firstName,
  lastName,
  email: `${firstName}@abc.edu`,
  enabled,
  roles: 'student',
  sectionId: 2,
  teamId: 1
})

const john = member(4, 'John', 'Smith', true)
const eric = member(5, 'Eric', 'Hudson', true)
const jerry = member(6, 'Jerry', 'Moon', false)

const submitted = (id, evaluatee) => ({
  peerEvaluationId: id,
  week: '2023-W32',
  evaluatorId: john.id,
  evaluatorName: 'John Smith',
  evaluateeId: evaluatee.id,
  evaluateeName: `${evaluatee.firstName} ${evaluatee.lastName}`,
  ratings: [{ ratingId: id, criterionId: 1, criterion: 'Quality of work', actualScore: 10 }],
  totalScore: 10,
  publicComment: '',
  privateComment: ''
})

describe('Peer evaluation form with a deactivated team member', () => {
  beforeEach(() => {
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept(
      'GET',
      '**/sections/*',
      envelope({ sectionId: 2, sectionName: '2023-2024', rubricId: 1 })
    )
    cy.intercept(
      'GET',
      '**/rubrics/*',
      envelope({
        rubricId: 1,
        criteria: [
          { criterionId: 1, criterion: 'Quality of work', description: 'Quality', maxScore: 10 }
        ]
      })
    )
    cy.intercept('POST', '**/evaluations/weeks/*/receipt', envelope(null))
  })

  function openForm(team, evaluations) {
    cy.intercept('GET', '**/students/teams/*', envelope(team)).as('team')
    cy.intercept('GET', '**/evaluations/evaluators/*/week/*', envelope(evaluations)).as(
      'evaluations'
    )
    cy.visit('/evaluations/submit-evaluations')
    cy.wait('@team')
    cy.wait('@evaluations')
  }

  function submitsCreated() {
    cy.intercept('POST', '**/evaluations', (req) => {
      req.reply(
        envelope(
          submitted(req.body.evaluateeId, {
            id: req.body.evaluateeId,
            firstName: 'Saved',
            lastName: 'Member'
          })
        ).body
      )
    }).as('submit')
  }

  it('leaves her out of a new form, and submitting sends nothing for her (2a)', () => {
    submitsCreated()
    openForm([john, eric, jerry], [])

    cy.get('.el-table')
      .should('contain', 'John Smith')
      .and('contain', 'Eric Hudson')
      .and('not.contain', 'Jerry Moon')

    cy.contains('button', 'Submit').click()

    cy.wait('@submit')
    cy.wait('@submit')
    cy.get('@submit.all').then((calls) => {
      expect(calls.map((call) => call.request.body.evaluateeId)).to.have.members([john.id, eric.id])
    })
  })

  it('leaves out an evaluation of her the student already submitted (2a)', () => {
    openForm([john, eric, jerry], [submitted(1, john), submitted(2, eric), submitted(3, jerry)])

    cy.get('.el-table')
      .should('contain', 'John Smith')
      .and('contain', 'Eric Hudson')
      .and('not.contain', 'Jerry Moon')
    cy.contains('button', 'Update').should('exist')
  })

  it('offers a new form if the only evaluation already submitted was of her (2a)', () => {
    openForm([john, eric, jerry], [submitted(3, jerry)])

    cy.get('.el-table')
      .should('contain', 'John Smith')
      .and('contain', 'Eric Hudson')
      .and('not.contain', 'Jerry Moon')
    cy.contains('button', 'Submit').should('exist')
  })

  it('on the 400 from a stale form, shows the message, sends no receipt, and reloads without her (9a)', () => {
    // Given: the form loaded while Jerry was active
    cy.intercept('POST', '**/evaluations/weeks/*/receipt', envelope(null)).as('receipt')
    cy.intercept('POST', '**/evaluations', (req) => {
      if (req.body.evaluateeId === jerry.id) {
        req.reply({
          statusCode: 400,
          body: { flag: false, code: 400, message: "This teammate's account is deactivated." }
        })
      } else {
        req.reply(envelope(submitted(req.body.evaluateeId, john)).body)
      }
    }).as('submit')
    openForm([john, eric, { ...jerry, enabled: true }], [])
    cy.get('.el-table').should('contain', 'Jerry Moon')

    // When: by the reload, she is deactivated and the other two are saved
    cy.intercept('GET', '**/students/teams/*', envelope([john, eric, jerry])).as('teamReloaded')
    cy.intercept(
      'GET',
      '**/evaluations/evaluators/*/week/*',
      envelope([submitted(1, john), submitted(2, eric)])
    ).as('evaluationsReloaded')
    cy.contains('button', 'Submit').click()

    // Then
    cy.get('.el-message--error').should('contain', "This teammate's account is deactivated.")
    cy.wait('@teamReloaded')
    cy.wait('@evaluationsReloaded')
    cy.get('.el-table')
      .should('contain', 'John Smith')
      .and('contain', 'Eric Hudson')
      .and('not.contain', 'Jerry Moon')
    cy.contains('button', 'Update').should('exist')
    cy.get('.el-message--success').should('not.exist')
    cy.get('@receipt.all').should('have.length', 0)
  })
})
